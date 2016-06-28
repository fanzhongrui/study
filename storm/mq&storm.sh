//http://singlefly.blog.51cto.com/4658189/1368579
//git

apt-get install git

//jdk (downloaded)
rpm -ivh jdk-7u51-linux-x64.rpm
echo "export JAVA_HOME=~/test/java/default" >> ~/.bashrc
source ~/.bashrc

//maven (downloaded)

mkdir ~/test/maven
tar zxvf apache-maven-3.3.9 -C ~/test/maven/
echo 'export M2_HOME=~/test/maven/apache-maven-3.3.9' >> ~/.bashrc
echo 'export M2=$M2_HOME/bin' >> ~/.bashrc
echo 'export MAVEN_OPTS="-Xms256m -Xmx512m"' >> ~/.bashrc
echo 'export PATH=$M2:$PATH' >> ~/.bashrc
source /etc/profile
mvn -v

//rocketMQ

	//install
wget https://github.com/alibaba/RocketMQ/archive/v3.0.8-beta1.tar.gz
tar zxvf v3.0.8-beta1.tar.gz
cd rocketmq
sh install.sh
cd devenv
	//config
//start nameserver
nuhup mqnamesrv &

//start broker
nohup mqbroker -n "192.168.0.1:9876;192.168.0.2:9876" &

//shutdown nameserver / broker
// sh ./mqshutdown
// mqshutdown broker | namesrv     


//zookeeper

wget mirro.bit.edu.cn/apache/zookeeper/zookeeper-3.4.8/zookeeper-3.4.8.tar.gz
tar zxvf zookeeper-3.4.8.tar.gz -C ~/test/zookeeper/
cp ~/test/zookeeper/conf/zoo_example.cfg ~/test/zookeeper/conf/zoo.cfg
echo 'ZOOKEEPER_HOME=~/test/zookeeper/zookeeper-3.4.8' >> ~/.bashrc
source ~/.bashrc

echo 'tickTime=2000' >> ~/test/zookeeper/conf/zoo.cfg
echo 'dataDir=~/test/zoodata/' >> ~/test/zookeeper/conf/zoo.cfg
echo 'clientPort=2181' >> ~/test/zookeeper/conf/zoo.cfg
echo 'initLimit=5' >> ~/test/zookeeper/conf/zoo.cfg
echo 'syncLimit=2' >> ~/test/zookeeper/conf/zoo.cfg
echo 'server.1=192.168.1.1:2888:3888' >> ~/test/zookeeper/conf/zoo.cfg
echo 'server.2=182.168.1.2:2888:3888' >> ~/test/zookeeper/conf/zoo.cfg
echo 'server.3=192.168.1.3:2888:3888' >> ~/test/zookeeper/conf/zoo.cfg


mkdir ~/test/zoodata
echo "1" >> ~/test/zoodata/myid  //'1'/'2'/'3'

//start zookeeper
sh $ZOOKEEPER_HOME/bin/zkServer.sh

//storm

// wget http://mirrors.tuna.tsinghua.edu.cn/apache/storm/apache-storm-1.0.1/apache-storm-1.0.1.zip
// unzip storm-1.0.1.zip

unzip jstorm-2.1.1.zip
mv jstorm-2.1.1 ~/test/jstorm/
echo 'export JSTORM_HOME=~/test/jstorm/jstorm-2.1.1' >> ~/.bashrc
echo 'PATH=$PATH:$JSTORM_HOME/bin' >> ~/.bashrc

// supervisor for web ui
mkdir ~/.jstorm
cp -f $JSTORM_HOME/conf/storm.yaml ~/.jstorm

//tomcat
tar -zxvf apache-tomcat-7.0.37.tar.gz -C ~/test/tomcat/
cp $JSTORM_HOME/jstorm-ui-1.0.1.war ~/test/tomcat/apache-tomcat-7.0.37/webapps
mv ROOT ROOT.old
ln -s jstorm-ui-1.0.1 ROOT
sh ~/test/tomcat/apache-tomcat-7.0.37/bin/startup.sh




