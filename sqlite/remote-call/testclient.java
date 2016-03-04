import java.io.BufferedInputStream;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.Socket;

import testSqlite.test;


public class testClient {
	public static void main(String[] args){
		Socket socket = null;
		ObjectInputStream is = null;
		test t;
		try{
			socket = new Socket("localhost",4700);
			PrintWriter pw = new PrintWriter(socket.getOutputStream(),true);
//			BufferedReader sysBuff = new BufferedReader(new InputStreamReader(System.in));
			pw.println("select * from test;");
			pw.flush();
			is = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
			Object obj ;
			while((obj=is.readObject())!=null){
				t = (test)obj;
				System.out.println("test: id="+t.id+" taskid="+t.taskid);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			try{
				if(is!=null)is.close();
				if(socket!=null)socket.close();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
}
