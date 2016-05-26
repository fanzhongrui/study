##maven使用入门
##一、pom.xml三个组成部分：  
- 项目管理部分
- 项目相关性部分
- 项目构建和报告部分

##二、使用入门 
- 项目主代码放到src/main/java/目录下（遵循maven的约定），无须额外配置，maven会自动搜索该目录找到项目主代码。
- Java类的包名与POM中定义的groupid和artifactID相吻合，一般来说，项目中的Java类的包都