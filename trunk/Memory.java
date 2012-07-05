class Memory {
	// This is the memory system component.
	private IntController hint;
	private int[] memoryWord;
	private int memorySize;

	// MMU: base and limit registers
	private int limitRegister; // specified in logical addresses
	private int baseRegister; // add base to get physical address

	// constructor
	public Memory(IntController i, int s) {
		// remember size and create memory
		hint = i;
		memorySize = s;
		memoryWord = new int[s];
		// Initialize with the dummy program
		init(0, 'J', 'P', 'A', 0);
	}

	// Access methods for the MMU: these are accessed by the kernel.
	// They do not check memory limits. It is interpreted as kernel's
	// fault to set these registers with values out of range. The result
	// is that the JVM will terminate with an 'out of range' exception
	// if a process uses a memory space that the kernel set wrongly.
	// This is correct: the memory interruption must be set in our
	// simulated machine only if the kernel was right and the process itself
	// tries to access an address which is out of its logical space.
	public void setLimitRegister(int val) {
		limitRegister = val;
	};

	public void setBaseRegister(int val) {
		baseRegister = val;
	};

	// Here goes some specifi methods for the kernel to access memory
	// bypassing the MMU (do not add base register or test limits)
	public int superRead(int address) {
		return memoryWord[address];
	}

	public void superWrite(int address, int data) {
		memoryWord[address] = data;
	}

	// Access methods for the Memory itself
	public synchronized void init(int add, int a, int b, int c, int d) {
		memoryWord[add] = (a << 24) + (b << 16) + (c << 8) + d;
	}

	public synchronized int read(int address) {
		if (address >= limitRegister) {
			hint.set(3);
			return 0;
		} else
			return memoryWord[baseRegister + address];
	}

	public synchronized void write(int address, int data) {
		if (address >= limitRegister)
			hint.set(3);
		else
			memoryWord[baseRegister + address] = data;
	}
}
