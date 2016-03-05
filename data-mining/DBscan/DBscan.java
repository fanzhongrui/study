package mining;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;


public class DBscan {
	double Eps = 3;//区域半径
	int MinPts = 4;//密度
	public Vector<DataObject> getNeighbors(DataObject p, ArrayList<DataObject> objects){
		Vector<DataObject> neighbors = new Vector<DataObject>();
		Iterator<DataObject> iter = objects.iterator();
		while(iter.hasNext()){
			DataObject q = iter.next();
			double[] arr1 = p.getVector();
			double[] arr2 = q.getVector();
			int len = arr1.length;
			if(calEuraDist(arr1, arr2, len)<=Eps){//使用欧氏距离
//			if(calCityDist(arr1, arr2, len)<=Eps){//使用街区距离
//			if(calCosDist(arr1, arr2, len)<=Eps){//使用向量夹角的余弦
				neighbors.add(q);
			}
		}
		return neighbors;
	}
	public int dbscann(ArrayList<DataObject> objects){
		int clusterId = 0;
		boolean AllVisited = false;
		while(!AllVisited){
			Iterator<DataObject> iter = objects.iterator();
			while(iter.hasNext()){
				DataObject p = iter.next();
				if(p.isVisited())continue;
				AllVisited = false;
				p.setVisited(true);//设为visited后就确定了它是核心点还是边界点
				Vector<DataObject> neighbors = getNeighbors(p, objects);
				if(neighbors.size()<MinPts){
					if(p.getCid()<=0)
						p.setCid(-1);//cid初始为0，表示未分类；分类后设置为一个正数；
				}else{
					if(p.getCid()<=0){
						clusterId++;
						expandCluster(p, neighbors, clusterId, objects);
					}else{
						int iid = p.getCid();
						expandCluster(p, neighbors, iid, objects);
					}
				}
				AllVisited = true;
			}
		}
		return clusterId;
	}
	public void expandCluster(DataObject p, Vector<DataObject> neighbors, int clusterID, ArrayList<DataObject> objects){
		p.setCid(clusterID);
		Iterator<DataObject> iter = neighbors.iterator();
		while(iter.hasNext()){
			DataObject q = iter.next();
			if(!q.isVisited()){
				q.setVisited(true);
				Vector<DataObject> qneighbors = getNeighbors(q, objects);
				if(qneighbors.size() >= MinPts){
					Iterator<DataObject> it = qneighbors.iterator();
					while(it.hasNext()){
						DataObject no = it.next();
						if(no.getCid() <= 0)no.setCid(clusterID);
					}
				}
			}
			if(q.getCid()<=0){//q不是任何簇的成员
				q.setCid(clusterID);
			}
		}
	}
	
	//欧氏距离
	public double calEuraDist(double[] a1, double[] a2, int len){
		double res = 0;
		for(int i=0; i<len; i++){
			res += (a1[i]-a2[i])*(a1[i]-a2[i]);
		}
		return Math.sqrt(res);
	}
	//曼哈顿距离，街区距离
	public double calCityDist(double[] a1, double[] a2, int len){
		double res = 0;
		for(int i=0; i<len; i++){
			res += Math.abs(a1[i]-a2[i]);
		}
		return res;
	}
	//夹角余弦
	public double calSinDist(double[] a1, double[] a2, int len){
		double res = 0;
		double t1=0, t2 = 0;
		for(int i=0; i<len; i++){
			res += a1[i]*a2[i];
			t1 += a1[i]*a1[i];
			t2 += a2[i]*a2[i];
		}
		t1 = Math.sqrt(t1);
		t2 = Math.sqrt(t2);
		return (res/t1)/t2;
	}
}
