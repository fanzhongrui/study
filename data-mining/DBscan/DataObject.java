
package mining;

public class DataObject {
	//public int mmsi;
	public int speed;
	public int depth;
	public int length;
	public int width;
	public boolean visited;
	public int cid;
	public DataObject(){
		visited = false;
		cid = 0;
	}
	public int getCid(){
		return cid;
	}
	public void setCid(int f){
		cid = f;
	}
	public double[] getVector(){
		double[] res = new double[5];
		int i=0;
		res[i++] = speed*1.0;
		res[i++] = depth*1.0;
		res[i++] = length*1.0;
		res[i++] = width*1.0;
		return res;
	}
	public boolean isVisited(){
		return visited;
	}
	public void setVisited(boolean f){
		visited = f;
	}
}
