class Kernel
{
	// Access to hardware components, including the processor
	private IntController hint;
	private Memory mem;
	private ConsoleListener con;
	private Timer tim;
	private Disk dis;
	private Processor pro;
	
	// Data used by the kernel
	private ProcessList readyList;
	private ProcessList diskList;
	
	// In the constructor goes initialization code
	public Kernel(IntController i, Memory m, ConsoleListener c, 
			Timer t, Disk d, Processor p)
	{
		hint = i;
		mem = m;
		con = c;
		tim = t;
		dis = d;
		pro = p;
		readyList = new ProcessList ("Ready");
		diskList = new ProcessList ("Disk");
		// Creates the dummy process
		readyList.pushBack( new ProcessDescriptor(0) );
		readyList.getBack().setPC(0);
	}
	
	// Each time the kernel runs it have access to all hardware components
	public void run(int interruptNumber)
	{
		// Calls the interface
		// You need to inform the PIDs from the ready and disk Lists
		SopaInterface.updateDisplay(readyList.getFront().getPID(), 
				readyList.getFront().getPID(), interruptNumber);

		ProcessDescriptor aux = null;

		// This is the entry point: must check what happened
		System.err.println("Kernel called for int "+interruptNumber);

		// save context
		readyList.getFront().setPC(pro.getPC());
		readyList.getFront().setReg(pro.getReg());
		switch(interruptNumber)
		{
			case 2: // HW INT timer
				aux = readyList.popFront();
				readyList.pushBack(aux);
				System.err.println("Time slice is over! CPU now runs: "+readyList.getFront().getPID());
				break;
			case 3:
				//TODO
				System.err.println("Illegal memory access!");
				break;
			case 5: // HW INT disk 
				aux = diskList.popFront();
				readyList.pushBack(aux);
				break;
			case 15: // HW INT console
				//TODO
				System.err.println("Operator typed " + con.getLine());
				break;
			case 36: // SW INT read
				aux = readyList.popFront();
				diskList.pushBack(aux);
				dis.roda(0,0,0);
				break;
			default:
				System.err.println("Unknown interrupt: " + interruptNumber);
		}

		// restore context
		pro.setPC(readyList.getFront().getPC());
		pro.setReg(readyList.getFront().getReg());
	}
}
