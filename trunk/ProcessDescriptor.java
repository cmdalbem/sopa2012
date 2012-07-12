import java.util.HashMap;

class ProcessDescriptor {
	private int PID;
	private int PC;
	private int[] reg;
	private ProcessDescriptor next;
	private int partition;
	private HashMap<Integer,FileDescriptor> files;
	private int time;
	private int nextFileId;
	private int flag;
	private FileDescriptor hangingFile=null;
	
	public final static int FLAG_RUNNING = 1;
	public final static int FLAG_LOADING = 2;
	public final static int FLAG_OPEN = 3;
	public final static int FLAG_CLOSE = 4;
	public final static int FLAG_GET = 5;
	public final static int FLAG_PUT = 6;

	public FileDescriptor openFile(int mod, int dis, int add)
	{
		FileDescriptor f = new FileDescriptor(nextFileId, this, mod, dis, add);
		files.put(nextFileId, f);
		++nextFileId;
		
		hangingFile = f; 
		
		return f;
	}
	
	public void removeFile(int id) { files.remove(id); }
	public void removeFile(FileDescriptor f) { files.remove(f.getId()); }
	public FileDescriptor getHangingFile() { return hangingFile; }
	
	public FileDescriptor getFile(int id)
	{
		return files.get(id);
	}
	
	public void setTime( int t ) { time = t; }
	synchronized public int tickTime()
	{
		//System.err.println("Process "+PID+" ticked "+time);
		return --time;
	}
	
	public boolean 	isLoading() { return flag==FLAG_LOADING; }
	public void 	resetFlag() { flag = FLAG_RUNNING; }
	public void		setFlag(int f) { flag = f; }
	public int		getFlag() { return flag; } 
	
	synchronized public int 	getPID() { return PID; }
	synchronized public int 	getPC() { return PC; }
	synchronized public void 	setPC(int i) { PC = i; }
	synchronized public int[] 	getReg() { return reg; }
	synchronized public void 	setReg(int[] r) { reg = r; }
	synchronized public int 	getPartition() { return partition; }
	synchronized public void 	setPartition(int p) { partition = p; }
	
	public ProcessDescriptor 	getNext() { return next; }
	public void 				setNext(ProcessDescriptor n) { next = n;}

	// Constructor
	public ProcessDescriptor(int pid, int p, boolean loading) {
		PID = pid;
		PC = 0;
		partition = p;
		flag = loading ? FLAG_LOADING : FLAG_RUNNING;
		reg = new int[16];
		files = new HashMap<Integer,FileDescriptor>();
		time = 0;
		nextFileId = 0;
	}
	

}

// This list implementation (and the 'next filed' in ProcessDescriptor) was
// programmed in a class to be faster than searching Java's standard lists,
// and it matches the names of the C++ STL. It is all we need now...

class ProcessList {
	private String myName = "No name";
	private ProcessDescriptor first = null;
	private ProcessDescriptor last = null;

	synchronized public ProcessDescriptor getFront() {
		return first;
	}

	synchronized public ProcessDescriptor getBack() {
		return last;
	}

	public ProcessList(String name) {
		myName = name;
		SopaInterface.addList(myName);
		Drawer.addList(myName);
	}

	synchronized public ProcessDescriptor popFront() {
		ProcessDescriptor n;
		if (first != null) {
			n = first;
			first = first.getNext();
			if (last == n)
				last = null;
			n.setNext(null);

			// Update interface
			SopaInterface.removeFromList(n.getPID(), myName);
			Drawer.removeFromList(n.getPID(), myName);

			return n;
		}
		return null;
	}

	synchronized public void pushBack(ProcessDescriptor n) {
		n.setNext(null);
		if (last != null)
			last.setNext(n);
		else
			first = n;
		last = n;

		// Update interface
		SopaInterface.addToList(n.getPID(), myName);
		Drawer.addToList(n.getPID(), myName);
	}
}