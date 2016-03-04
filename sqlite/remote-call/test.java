package testSqlite;

public class test implements java.io.Serializable {  
    private static final long serialVersionUID = 1L; 
	int id;
	String taskid;
	test(int id, String taskid){
		this.id = id;
		this.taskid = taskid;
	}
	test(){
		
	}
}
