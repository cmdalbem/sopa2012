import java.util.ArrayList;

class ProcessDescriptor {
	private int PID;
	private int PC;
	private int[] reg;
	private ProcessDescriptor next;
	private int partition;
	private boolean isloading;
	private ArrayList<FileDescriptor> files;
	private int time;

	public FileDescriptor addFile(Memory mem)
	{
		FileDescriptor f = new FileDescriptor(files.size(), this, mem);
		files.add(files.size(), f);
		
		return f;
	}
	
	public void removeFile(int id)
	{
		files.remove(id);
	}
	
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
	
	public boolean 	isLoading() { return isloading; }
	public void 	setLoaded() { isloading = false; }
	
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
		isloading = loading;
		reg = new int[16];
		files = new ArrayList<FileDescriptor>();
		time = 0;
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