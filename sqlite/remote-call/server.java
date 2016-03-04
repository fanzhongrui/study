package testSqlite;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class server {
	Connection conn;
	Statement statement;
	public server(){
		conn = this.getSQliteConnection();
		statement = this.getStatement(conn);
	}
	private Connection getSQliteConnection(){
		try{
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection("jdbc:sqlite:memory:st.db");
			return conn;
		}catch(Exception e){
			e.printStackTrace();
			return null;
		}
	}
	private Statement getStatement(Connection con){
		try{
			statement = con.createStatement();
		}catch(Exception e){
			e.printStackTrace();
		}
		return statement;
	}
	public void loadSql(Statement s){
		String sql0 = "drop table if exists test";
		String sql1 = "create table test(id int4, taskid varchar(30));";
		String sqlInsert = "insert into test values (1,'2');";
		String sqlInsert2 = "insert into test values (3,'4');";
		String query = "select * from test";
		try{
			s.execute(sql0);
			s.execute(sql1);
			s.executeUpdate(sqlInsert);
			s.executeUpdate(sqlInsert2);
			ResultSet rs = s.executeQuery(query);
			while(rs.next()){
				int id = rs.getInt("id");
				String taskid = rs.getString("taskid");
				System.out.println("id="+id+" taskid="+taskid);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	public void startServer(){
		ServerSocket server = null;
		try{
			server = new ServerSocket(4700);
		}catch(Exception e){
			e.printStackTrace();
		}
		while(true){
			Socket socket = null;
			ObjectOutputStream os = null;
			BufferedReader br = null;
			try{
				socket = server.accept();
				//使用accept()阻塞等待客户端请求，有客户请求到来则产生一个Socket对象,然后返回数据
//				System.out.println(socket.getLocalPort());
			
				br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				os = new ObjectOutputStream(socket.getOutputStream());
				
				String sql = br.readLine();
				System.out.println("sql:"+sql);
				ResultSet rs;
				try{
					rs = statement.executeQuery(sql);
					while(rs.next()){
						test t = new test();
						t.id = rs.getInt("id");
						t.taskid = rs.getString("taskid");
						System.out.println(t.id+" "+t.taskid);
						os.writeObject(t);
						
					}
					os.writeObject(null);
					os.flush();
				}catch(Exception e){
					e.printStackTrace();
				}
			}catch(Exception  e){
				e.printStackTrace();
			}finally{
				try{
					if(os != null)os.close();
					if(socket!=null)socket.close();
					if(br!=null)br.close();
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}
	}

	public static void main(String[] args){
		server s = new server();
		s.loadSql(s.statement);
		s.startServer();
	}
}
