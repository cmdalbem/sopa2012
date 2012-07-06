import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;

class Kernel
{
	// Access to hardware components, including the processor
	private IntController hint;
	private Memory mem;
	private ConsoleListener con;
	private Timer tim;
	private Disk disk;
	private Processor pro;
	
	// Data used by the kernel
	private int nextPid;
	private ProcessList readyList;
	private ProcessList diskList;
	private int[] partitionList;
	private int npartitions;
	
	private final int PART_SISOP=0;
	private final int PART_USED=1;
	private final int PART_FREE=2;
	
	
	// In the constructor goes initialization code
	public Kernel(IntController i, Memory m, ConsoleListener c, 
			Timer t, Disk d, Processor p)
	{
		hint = i;
		mem = m;
		con = c;
		tim = t;
		disk = d;
		pro = p;
		readyList = new ProcessList ("Ready");
		diskList = new ProcessList ("Disk");
		nextPid = 2;
		
		npartitions = 6;
		partitionList = new int[npartitions];
		partitionList[0] =partitionList[1] = PART_SISOP;
		for(int it=2; it<npartitions; it++)
			partitionList[it] = PART_FREE;
		
		// Creates the dummy process
		createDummyProcess();
		setProcessContext( readyList.getFront() );
	}
	
	private int findFreePartition()
	{
		for(int i=2; i<npartitions; i++)
			if(partitionList[i]==PART_FREE)
				return i;
		
		return -1;
	}

	public void createDummyProcess()
	{
		ProcessDescriptor newProc = new ProcessDescriptor(0, 0, false);
		readyList.pushBack( newProc );
	}
	
	public ProcessDescriptor createProcess()
	{
		int part = findFreePartition();
		if(part==-1)
			return null;
		else
		{
			ProcessDescriptor newProc = new ProcessDescriptor(nextPid++, part, true);
			partitionList[part] = PART_USED;
			
			return newProc;
		}
	}
	
	public void setProcessContext(ProcessDescriptor p)
	{
		if(p.getPID()==0)
		{
			readyList.popFront();
			readyList.pushBack(p);
			p = readyList.getFront();
		}
		pro.setPC( p.getPC() );
		pro.setReg( p.getReg() );
		mem.setBaseRegister( p.getPartition() * mem.getPartitionSize() );
		mem.setLimitRegister( p.getPartition() * mem.getPartitionSize() + mem.getPartitionSize() - 1 );
		System.err.println("new process pc: " + pro.getPC());
	}
	
	// Each time the kernel runs it have access to all hardware components
	public void run(int interruptNumber)
	{
		// Calls the interface
		// You need to inform the PIDs from the ready and disk Lists
		SopaInterface.updateDisplay(readyList.getFront().getPID(), readyList.getFront().getPID(), interruptNumber);

		ProcessDescriptor aux = null;

		// This is the entry point: must check what happened
		System.err.println("Kernel called for int " + interruptNumber);

		// save context
		readyList.getFront().setPC(pro.getPC());
		readyList.getFront().setReg(pro.getReg());
		switch(interruptNumber)
		{
			/////////////////////////
			// HARDWARE INTERRUPTS //
			/////////////////////////
			case 2: // HW INT timer
				readyList.getFront().setPC(pro.getPC());
				readyList.getFront().setReg(pro.getReg());
				aux = readyList.popFront();
				readyList.pushBack(aux);
				
				setProcessContext( readyList.getFront() );
				
				System.err.println("Time slice is over! CPU now runs: "+readyList.getFront().getPID());
				break;
			case 3:
				System.err.println("Illegal memory access!");
				break;
			case 5: // HW INT disk 
				if(disk.getError()==disk.ERRORCODE_SUCCESS)
				{
					aux = diskList.popFront();
					if(aux.isLoading())
					{
						//write on memory the loaded data
						aux.setLoaded();
						for(int i=0; i<disk.getSize(); i++)
							mem.superWrite(aux.getPartition()*mem.getPartitionSize() +i, disk.getData(i));
					}
					readyList.pushBack( aux );
				}				
				else
				{
					switch(disk.getError())
					{
						case 1: //disk.ERRORCODE_SOMETHING_WRONG:
							System.err.println("Error trying to read from disc: something went wrong!");
							break;
						case 2: //disk.ERRORCODE_ADDRESS_OUT_OF_RANGE:
							System.err.println("Error trying to read from disc: address out of range.");
							break;
						case 3: //disk.ERRORCODE_MISSING_EOF:
							System.err.println("Error trying to read from disc: missing EOF.");
							break;
					}
				}
				break;
			case 15: // HW INT console
				int[] val = new int[2];
				boolean success = true;
				
				// parse the user's entry
				StreamTokenizer tokenizer = new StreamTokenizer( new StringReader(con.getLine()) );
				try {
					for(int i=0; i<2; i++)
						if(tokenizer.nextToken() != StreamTokenizer.TT_EOF
							&& tokenizer.ttype == StreamTokenizer.TT_NUMBER)
								val[i] = (int) tokenizer.nval;
				} catch (IOException e) { success=false; }
				
				if(success)
				{
					// create the process without inserting it on readyList
					aux = createProcess();
					if(aux!=null)
					{
						diskList.pushBack(aux);					
						disk.roda(disk.OPERATION_LOAD,val[1],0);
					}
				}
				
				break;
			
			/////////////////////////
			// SOFTWARE INTERRUPTS //
			/////////////////////////
			case 32: // EXIT
				aux = readyList.popFront();
				setProcessContext( readyList.getFront() );
				break;
				
			case 34: // OPEN
				//TODO
				break;
				
			case 35: // CLOSE
				//TODO
				break;
				
			case 36: // GET
				aux = readyList.popFront();
				diskList.pushBack(aux);
				disk.roda(0,0,0);
				break;
			
			case 37: // PUT
				//TODO
				break;
				
			default:
				System.err.println("Unknown interrupt: " + interruptNumber);
		}

		// restore context
		pro.setPC(readyList.getFront().getPC());
		pro.setReg(readyList.getFront().getReg());
	}
}
