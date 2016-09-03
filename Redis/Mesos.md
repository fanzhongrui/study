##Mesos
Mesos是一个开源的资源管理系统，可以对集群中的资源做弹性管理.
Yarn是从MapReduce中演化而来的，因而在大数据处理中扮演重要角色，但它现在还不能看做是一个通用的资源管理系统，太多的内部实现过于狭隘，比如资源申请和分配模型，对长服务的支持等。

对比：
                    Mesos                                Yarn
实现语言       C++                                  Java
基本架构   master/slaves                           master/slaves

资源分配   仿borg，可能出现资源饿死          基于资源预留的方案，资源利用率低下
对Hadoop支持 粗粒度支持，Hadoop直接以服务形式运行 细粒度支持，每个MR/Spark作业均是一个短应用
对docker支持   中等                                  很差
社区参与者       较少                                  众多
对长服务的支持 好                                       一般
两者关系    相互借鉴

目前Yarn更多地用在大数据平台上，对上层计算框架支持的非常好，
Mesos更多是定位在资源的抽象和管理上，以便支持各种应用，不仅仅是计算框架。

Mesos中包含四类主要的服务（实际上是一个socket Server），它们分别是Mesos Master,Mesos Slave, SchedulerProcess和ExecutorProcess，它们之间通过Protocal Buffer消息