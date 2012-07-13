class FileDescriptor
{
	private ProcessDescriptor proc;
	
	private int address;
	private int disk;
	private int mode;
	private int size;
	private int pos;
	private int id;
	
	public final static int FILEMODE_W = 0;
	public final static int FILEMODE_R = 1;
	
	public FileDescriptor(int i, ProcessDescriptor p, int mod, int dis, int add)
	{
		id = i;
		proc = p;
		
		mode = mod;
		address = add;
		disk = dis;
		pos = 0;
	}

	public int getId() { return id; }
	public int getDisk() { return disk; }
	public int getMode() { return mode; }
	public int getPos() { return pos; }
	public int getSize() { return size; }
	public int getAddress() { return address; }
	
	public void incPos() { ++pos; }
	public void incSize() { ++size; }
	
	public void setSize(int s) { size = s; }
}