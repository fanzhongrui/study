##Thrift学习
###背景
Thrift是一个跨语言的服务部署框架，通过一个中间语言（IDL，接口定义语言），来定义RPC的接口和数据类型，然后通过一个编译器生成不同语言的代码，并由生成的代码负责RPC协议层和传输层的实现。

Thrift由两部分组成：编译器（在compiler目录下，采用C++编写）和服务器（在lib目录下），其中编译器的作用是将用户定义的Thrift文件编译生成对应语言的代码，而服务器是事先已经实现好的、可供用户直接使用的RPC Server（用户也可以编写自己的server）。


同大部分编译器一样，Thrift编译器（采用C++语言编写）也分为词法分析、语法分析等步骤。

Thrift使用了开源的flex和Bison进行词法语法分析（具体见Thrift.ll和thrift.yy），经过语法分析后，Thrift根据对应语言的模板（在compiler\cpp\src\generate目录下）生成相应的代码。对于服务器实现而言，Thrift仅包含比较经典的服务器模型，比如单线程模型（TSimpleServer），线程池模型（TThreadPoolServer）、一个请求一个线程（TThreadServer）和非阻塞模型（TNonblockingServer）等。

Server端最重要的类是LogSenderProcessor，它内部有一个映射关系processMap_，保存了所有RPC函数名到函数实现句柄的映射，对于LogSender而言，它只保存了一个RPC映射关系。
LogSenderProcessor中一个最重要的函数式process()，它是服务器的主体函数，服务器端（socket Server）监听到客户端有请求到达后，会检查消息类型，并检查processMap_映射，找到对应的消息处理函数，并找到对应的消息处理函数，并调用之（这里可以采用各种并发模型，比如one-request-one-thread, thread-pool等）。
Thrift最重要的组件是编译器（采用C++编写），它为用户生成了网络通信相关的代码，从而大大减少了用户的编码工作。