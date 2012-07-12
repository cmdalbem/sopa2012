class Memory {
	// This is the memory system component.
	private int[] memoryWords;
	private int partitionSize;
	private int npartitions;

	// constructor
	public Memory(int ps, int np) {
		// remember size and create memory
		partitionSize = ps;
		npartitions = np;
		memoryWords = new int[ps*np];
		
		// Initialize dummy program
		init(0, 'J', 'P', 'A', 0);
	}

	// for testing only!!
	public void print()
	{
		for(int i=0; i<partitionSize*npartitions; i++)
			System.err.println(memoryWords[i]);
	}
	
	public int getPartitionSize() { return partitionSize; }
	public int getPartitionsNumber() { return npartitions; }
	
	// Access methods for the MMU: these are accessed by the kernel.
	// They do not check memory limits. It is interpreted as kernel's
	// fault to set these registers with values out of range. The result
	// is that the JVM will terminate with an 'out of range' exception
	// if a process uses a memory space that the kernel set wrongly.
	// This is correct: the memory interruption must be set in our
	// simulated machine only if the kernel was right and the process itself
	// tries to access an address which is out of its logical space.


	// Here goes some specific methods for the kernel to access memory
	// bypassing the MMU (do not add base register or test limits)
	synchronized public int superRead(int address) {
		return memoryWords[address];
	}

	synchronized public void superWrite(int address, int data) {
		memoryWords[address] = data;
	}

	// Access methods for the Memory itself
	public synchronized void init(int address, int a, int b, int c, int d) {
		memoryWords[address] = (a << 24) + (b << 16) + (c << 8) + d;
	}
}

class MMU
{
	// MMU: base and limit registers
	private int limitRegister; // specified in logical addresses
	private int baseRegister; // add base to get physical address
	private IntController hint;
	private Memory mem;
	
	public MMU(Memory m, IntController i)
	{
		mem = m;
		hint = i;
	}
	
	public void setLimitRegister(int val)
	{
		limitRegister = val;
	}
	
	public void setBaseRegister(int val)
	{
		baseRegister = val;
	}
	
	public synchronized int read(int address) {
		if (address >= limitRegister) {
			hint.set(3); //memory access violation interruption
			return 0;
		} else
			return mem.superRead(baseRegister + address);
	}

	public synchronized void write(int address, int data) {
		if (address >= limitRegister)
			hint.set(3); //memory access violation interruption
		else
			mem.superWrite(baseRegister + address, data);
	}
}
