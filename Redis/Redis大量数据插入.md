###Redis大量数据插入
有时，Redis实例需要装载大量用户在短时间内产生的数据，数以百万计的keys需要被快速的创建。即大量数据插入（mass insertion）

####使用Luke协议
使用正常模式的Redis客户端执行大量数据插入不是一个好主意：因为一个个的插入会有大量的时间浪费在每一个命令往返时间上。  
使用管道（pipelining）还比较靠谱，但是在大量插入数据的同时又需要执行其他新命令时，这时读取数据的同时需要确保尽可能快的写入数据。  
只有一小部分的客户端支持非阻塞／输出（non-blocking I/O），并且并不是所有客户端能以最大限度的提高吞吐量到高效的方式来分析答复。  
例如，如果需要生成一个10亿度keyN->valueN的大数据集，会创建一个如下的redis命令集的文件：  
set key0 value0
set key1 value1
...
set keyn valuen
一旦创建这个文件，其余的就是让Redis尽可能快的执行，例如：  
(cat data.txt; sleep 10) | nc localhost 6379 > /dev/null  
然而，这并不是一个非常可靠大方式，因为netcat进行大规模插入时不能检查错误。  
从Redis 2.6开始redis-cli支持一种新的被称之为pipe mode的新模式用于执行大量数据插入工作。  
使用pipe mode模式的执行命令如下：  
cat data.txt | redis-cli --pipe  
这将产生类似如下的输出：  
All data transferred. Waiting for the last reply...  
Last reply received from server.  
errors: 0, replies: 1000000  
使用redis－cli将有效的确保错误输出到Redis实例的标准输出里面。  
####生成Redis协议
它会非常简单的生成和解析Redis协议。但是为了生成大量数据插入的目标，就需要了解每一个细节协议，每个命令会用如下方式表示：  
*<args><cr><lf>
&<len><cr><lf>
<arg0><cr><lf>
<arg1><cr><lf>
...
<argN><cr><lf>
这里的<cr>是“\r”（或者是ASCII的13）、<lf>是“\n”（或者是ASCII的10）。
例如，命令set key value协议格式如下：
*3<cr><lf>  
$3<cr><lf>  
set<cr><lf>  
$3<cr><lf>  
key<cr><lf>  
$5<cr><lf>  
value<cr><lf>  
或表示为引用字符串：  
“*3\r\n$3\r\nset\r\n$3\r\nkey\r\n$5\r\n$5\r\nvalue\r\n”  
你需要将大量插入数据的命令按照上面的方式一个接一个的生成到文件。  
然后可以直接用redis-cli的pipe执行大量数据插入命令：redis-cli --pipe
####pipe mode的工作原理
难点是保证redis-cli在pipe mode模式下执行和netcat一样快的同时，如何能理解服务器发送的最后一个回复。  
这是通过以下方式获得：
*redis-cli --pipe试着尽可能快的发送数据到服务器  
*读取数据的同时，解析它  
*一旦没有更多的数据输入，它就会发送一个特殊的echo命令，后面跟着20个随机的字符。我们相信可以通过匹配回复相同的20个字符是同一个命令的行为  
*一旦这个特殊命令发出，收到的答复就开始匹配这20个字符，当匹配时，就可以成功退出了  

同时，在分析回复的时候，我们会采用计数器的方法计数，以便在最后能够告诉我们大量插入数据的数据量  

























