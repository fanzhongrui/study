###Redis管道
####请求/响应协议和RTT
Redis是一种基于客户端-服务端模型以及请求/响应协议的TCP服务。
通常情况下一个请求会遵循以下步骤：  
- 客户端向服务端发送一个查询请求，并监听Socket返回，通常是以阻塞模式，等待服务端响应。  
- 服务端处理命令，并将结果返回给客户端。  
例如，下面是4个命令序列的执行情况：  
```
- Client： incr X
- Server: 1
- Client: incr X
- Server: 2
- Client: incr X
- Server: 3
- Client: incr X
- Server: 4
```
客户端和服务器通过网络进行连接。这个连接可以很快（loopback接口）或很慢（建立了一个多次跳转的网络连接）。  
无论网络延如何延时，数据包总是能从客户端到达服务器，并从服务器返回数据回复客户端。  
这个时间被称之为 RTT (Round Trip Time - 往返时间). 当客户端需要在一个批处理中执行多次请求时很容易看到这是如何影响性能的（例如添加许多元素到同一个list，或者用很多Keys填充数据库）。  
例如，如果RTT时间是250毫秒（在一个很慢的连接下），即使服务器每秒能处理100k的请求数，每秒最多也只能处理4个请求。  
如果采用loopback接口，RTT就短得多（比如我的主机ping 127.0.0.1只需要44毫秒），但在一次批量写入操作中，这仍然是一笔很大的开销。  
好在管道可以改善这种情况。
####Redis管道（Pipelining）
非阻塞请求/响应服务器能够实现异步处理，即使旧的请求还未被响应，也能处理新的请求。  
这样就可以将多个命令发送到服务器，而不用等待回复，可以在最后一个步骤中读取所有回复。这就是管道（pipelining）。  
下面是一个使用管道的例子：  
```
$ (printf "PING\r\nPING\r\nPING\r\n"; sleep 1) | nc localhost 6379
+PONG
+PONG
+PONG
```
这一次不是每个命令都花费了RTT开销，而是只用了一个命令的开销时间。  
当然，用管道操作的第一个例子如下：  
```
    Client: INCR X
    Client: INCR X
    Client: INCR X
    Client: INCR X
    Server: 1
    Server: 2
    Server: 3
    Server: 4
```
>**注意：使用管道发送命令时，服务器将不得不回复一个响应队列，这会占用很多内存。所以，如果需要发送大量命令，最好还是把它们按照适当数量分批次执行。**  

###Redis大量数据插入
有时，Redis实例需要装载大量用户在短时间内产生的数据，数以百万计的keys需要被快速的创建。即大量数据插入（mass insertion）

####使用Luke协议
使用正常模式的Redis客户端执行大量数据插入是不明智的：因为一个个的插入会有大量的时间浪费在每一个命令往返时间上。  
使用管道（pipelining）还比较靠谱，但是在大量插入数据的同时又需要执行其他新命令时，这时读取数据的同时需要确保尽可能快的写入数据。  
只有一小部分的客户端支持非阻塞／输出（non-blocking I/O），并且并不是所有客户端能以最大限度的提高吞吐量到高效的方式来分析答复。  
例如，如果需要生成一个10亿度keyN->valueN的大数据集，会创建一个如下的redis命令集的文件：
```  
set key0 value0
set key1 value1
...
set keyn valuen
```
一旦创建这个文件，其余的就是让Redis尽可能快的执行，例如：  
```  
(cat data.txt; sleep 10) | nc localhost 6379 > /dev/null  
```
然而，这并不是一个非常可靠大方式，因为netcat进行大规模插入时不能检查错误。  
从Redis 2.6开始redis-cli支持一种新的被称之为**pipe mode**的新模式用于执行大量数据插入工作。  
使用**pipe mode**模式的执行命令如下：  
```  
cat data.txt | redis-cli --pipe  
```
这将产生类似如下的输出：  
```
All data transferred. Waiting for the last reply...  
Last reply received from server.  
errors: 0, replies: 1000000  
```
使用redis－cli将有效的确保错误输出到Redis实例的标准输出里面。  
####生成Redis协议
它会非常简单的生成和解析Redis协议。但是为了生成大量数据插入的目标，就需要了解每一个细节协议，每个命令会用如下方式表示：  
```
*<args><cr><lf>
&<len><cr><lf>
<arg0><cr><lf>
<arg1><cr><lf>
...
<argN><cr><lf>
```
这里的<cr>是“\r”（或者是ASCII的13）、<lf>是“\n”（或者是ASCII的10）。
例如，命令**set key value**协议格式如下：  
```
*3<cr><lf>  
$3<cr><lf>  
set<cr><lf>  
$3<cr><lf>  
key<cr><lf>  
$5<cr><lf>  
value<cr><lf>
```
或表示为引用字符串：  
```
"3\r\n$3\r\nset\r\n$3\r\nkey\r\n$5\r\n$5\r\nvalue\r\n"
```
你需要将大量插入数据的命令按照上面的方式一个接一个的生成到文件。  
然后可以直接用redis-cli的pipe执行大量数据插入命令：
```
redis-cli --pipe
```
####pipe mode的工作原理
难点是保证redis-cli在pipe mode模式下执行和netcat一样快的同时，如何能理解服务器发送的最后一个回复。  
这是通过以下方式获得： 

- redis-cli --pipe试着尽可能快的发送数据到服务器  
- 读取数据的同时，解析它  
- 一旦没有更多的数据输入，它就会发送一个特殊的echo命令，后面跟着20个随机的字符。我们相信可以通过匹配回复相同的20个字符是同一个命令的行为  
- 一旦这个特殊命令发出，收到的答复就开始匹配这20个字符，当匹配时，就可以成功退出了  

同时，在分析回复的时候，我们会采用计数器的方法计数，以便在最后能够告诉我们大量插入数据的数据量  

























