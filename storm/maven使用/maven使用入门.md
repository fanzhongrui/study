##maven使用入门
##一、pom.xml三个组成部分：  
- 项目管理部分
- 项目相关性部分
- 项目构建和报告部分

##二、使用入门 
- 项目主代码放到src/main/java/目录下（遵循maven的约定），无须额外配置，maven会自动搜索该目录找到项目主代码。
- Java类的包名与POM中定义的groupid和artifactID相吻合  
- 一般maven坐标写成如下格式：groupId:artifactId:packaging:version

pom.xml文件示例：
'''
 <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
     <modelVersion>4.0.0</modelVersion> 

     <groupId>com.helloworld</groupId> 
     <artifactId>hello-world</artifactId> 
     <version>1.0-SNAPSHOT</version> 
     <packaging>jar</packaging> 

     <name>helloworld</name> 

     <properties> 
       <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding> 
     </properties>

 <dependencies>
       <dependency> 
         <groupId>junit</groupId> 
         <artifactId>junit</artifactId> 
         <version>3.8.1</version> 
         <scope>test</scope> 
       </dependency> 
     </dependencies> 

    </project>    
'''
