package mining;

import java.util.ArrayList;

//回归分类树节点
public class AttrNode {
	public String attrName;//节点属性名字
	public int nodeIndex;//节点索引标号
	public int leafNum;//包含的叶子节点数
	public double alpha;//节点误差率
	public String parentAttrValue;//父亲分类属性值
	public AttrNode[] childAttrNode;//孩子节点
	public ArrayList<String> dataIndex;//数据记录索引
}
