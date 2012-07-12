import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.Queue;

class Kernel
{
	// Access to hardware components, including the processor
	private IntController hint;
	private Memory mem;
	private ConsoleListener con;
	private Timer tim;
	private Disk[] disks;
	private Processor[] procs;
	
	// Data used by the kernel
	private int nextPid;
	private ProcessList[] cpuLists;
	private ProcessList readyList;
	private ProcessList[] diskLists;
	private int[] partitionList;
	private int npartitions;
	private int ncpus;
	
	class DiskRequest
	{
		public int disk;
		public int op;
		public int add;
		public int data;
		
		public DiskRequest(int whichdisk, int operation, int address, int wdata)
		{
			disk = whichdisk;
			op = operation;
			add = address;
			data = wdata;
		}
	}
	private Queue<DiskRequest> requests;
	
	
	private final int PART_SISOP=0;
	private final int PART_USED=1;
	private final int PART_FREE=2;
	
	// In the constructor goes initialization code
	public Kernel(IntController hi, Memory m, ConsoleListener c, 
			Timer t, Disk d1, Disk d2, int ncps, int nparts)
	{
		hint = hi;
		mem = m;
		con = c;
		tim = t;
		nextPid = 1;
		ncpus = ncps;
		npartitions = nparts;
		
		disks = new Disk[2];
		disks[0] = d1;
		disks[1] = d2;
		requests = new LinkedList<DiskRequest>();
		
		readyList = new ProcessList ("Ready");
		
		cpuLists = new ProcessList[ncpus];
		for(int i=0; i<ncpus; i++)
			cpuLists[i] = new ProcessList ("CPU " + i);
		
		diskLists = new ProcessList[2];
		diskLists[0] = new ProcessList ("Disk 0");
		diskLists[1] = new ProcessList ("Disk 1");
		
		partitionList = new int[npartitions];
		partitionList[0] = partitionList[1] = PART_SISOP;
		for(int it=2; it<npartitions; it++)
			partitionList[it] = PART_FREE;
	}
	
	public void init(Processor[] ps)
	{
		procs = ps;
		
		for(int i=0; i<procs.length; i++)
			runProcess( createDummyProcess(), i );
		
		createInitialProcesses(Config.NINITIALPROCESSES, Config.PROCSINITALPOS);		
	}
	
	private void createInitialProcesses(int count,  int initPos)
	{
		ProcessDescriptor paux;
		int disk=0;
		
		for(int i=0; i<count; i++)
		{
			disk = (int) (Math.random()*2);
			paux = createProcess();
			if(paux!=null)
			{
				diskLists[disk].pushBack(paux);					
				requests.add( new DiskRequest(disk, disks[disk].OPERATION_LOAD, initPos, 0) );
			}
		}
		
		//launch the first request, which will subsequently launch the others
		DiskRequest r = requests.poll();
		disks[r.disk].roda(r.op, r.add, r.data);
	}
	
	public ProcessDescriptor createDummyProcess()
	{
		return new ProcessDescriptor(0, 0, false);	
	}
	
	private int findFreePartition()
	{
		for(int i=2; i<npartitions; i++)
			if(partitionList[i]==PART_FREE)
				return i;
		
		return -1;
	}
	
	public ProcessDescriptor createProcess()
	{
		int part = findFreePartition();
		if(part==-1)
		{
			System.out.println("Error creating new process: no memory available.");
			return null;
		}			
		else
		{
			ProcessDescriptor newProc = new ProcessDescriptor(nextPid++, part, true);
			partitionList[part] = PART_USED;
			
			return newProc;
		}
	}
	
	public void runProcess(ProcessDescriptor p, int procId)
	{
		if(p==null)
			p = createDummyProcess();
		
		//System.err.println("Sending to execute process " + p.getPID() + " with PC: " + p.getPC());
		procs[procId].setPC( p.getPC() );
		procs[procId].setReg( p.getReg() );
		procs[procId].getMMU().setBaseRegister( p.getPartition() * mem.getPartitionSize() );
		procs[procId].getMMU().setLimitRegister( p.getPartition() * mem.getPartitionSize() + mem.getPartitionSize() - 1 );
		p.setTime((int) (Config.minSlice + Math.random()*(Config.maxSlice-Config.minSlice))); //set some random slice time
		
		cpuLists[procId].pushBack(p);
	}
	
	private void killCurrentProcess(int procId)
	{
		killProcess( cpuLists[procId].popFront() );
		runProcess( readyList.popFront(), procId );
	}
	
	private void killProcess(ProcessDescriptor p)
	{
		partitionList[p.getPartition()] = PART_FREE;
	}
	
	synchronized private void saveContext(int cpu)
	{
		int pc;
		int[] reg;
		ProcessDescriptor paux = null;
		
		paux = cpuLists[cpu].getFront();
		pc = procs[cpu].getPC();
		paux.setPC(pc);
		
		paux = cpuLists[cpu].getFront();
		reg = procs[cpu].getReg();
		paux.setReg(reg);
	}
	
	synchronized private void restoreContext(int cpu)
	{
		int pc;
		int[] reg;
		ProcessDescriptor paux = null;
		
		paux = cpuLists[cpu].getFront();
		pc = paux.getPC();
		procs[cpu].setPC(pc);
		
		paux = cpuLists[cpu].getFront();
		reg = paux.getReg();
		procs[cpu].setReg(reg);
	}
	
	private void handleTerminal()
	{
		int[] val = new int[2];
		boolean success = true;
		ProcessDescriptor paux = null;
		
		// parse the user's entry
		StreamTokenizer tokenizer = new StreamTokenizer( new StringReader(con.getLine()) );
		try {
			for(int i=0; i<2; i++)
				if(tokenizer.nextToken() != StreamTokenizer.TT_EOF
					&& tokenizer.ttype == StreamTokenizer.TT_NUMBER)
						val[i] = (int) tokenizer.nval;
		} catch (IOException e) {
			success=false;
			System.out.println("Could not parse user's entry.");
		}
		
		if(success)
		{
			if(val[0]==0 || val[0]==1)
			{
				// create the process without inserting it on readyList
				paux = createProcess();
				if(paux!=null)
				{
					diskLists[val[0]].pushBack(paux);
					DiskRequest r = new DiskRequest(val[0], disks[val[0]].OPERATION_LOAD, val[1], 0); 
					if(requests.isEmpty())
						disks[r.disk].roda(r.op, r.add, r.data);
					else
						requests.add(r);
					
				}
			}
			else
				System.out.println("Invalid disk entered: please choose Disk 0 or 1.");
		}
	}
	
	synchronized private void handleTimerInt()
	{
		ProcessDescriptor paux = null;
		
		for(int i=0; i<ncpus; i++)
		{
			paux = cpuLists[i].getFront();
			
			if( paux!=null && paux.tickTime()==0 )
			{				
				System.err.println("Time is over for process " + cpuLists[i].getFront().getPID() + ", saving the PC=" + procs[i].getPC());
				procs[i].sem.P();
					paux.setPC(procs[i].getPC());
					paux.setReg(procs[i].getReg());
				procs[i].sem.V();
				
				paux = cpuLists[i].popFront();
				if(paux.getPID()!=0)
					readyList.pushBack(paux);
				
				paux = readyList.popFront();
				
				runProcess( paux, i );
				
				System.err.println("Time slice is over! CPU " + i + " now runs: " + cpuLists[i].getFront().getPID());
			}
		}
	}
	
	synchronized private void updateInterface(int interruptNumber, int cpu)
	{
		SopaInterface.updateDisplay(cpuLists[cpu].getFront().getPID(), interruptNumber);
		Drawer.drawEvent(interruptNumber, cpu);
	}
	
	synchronized private void handleDiskInt(int d)
	{
		ProcessDescriptor paux = null;
		
		paux = diskLists[d].popFront();
		
		// check if reading attempt was succeeded
		if(disks[d].getError()==disks[d].ERRORCODE_SUCCESS)
		{
			if(paux.isLoading())
			{
				//write on memory the loaded data
				paux.setLoaded();
				for(int i=0; i<disks[d].getSize(); i++)
					mem.superWrite(paux.getPartition()*mem.getPartitionSize() +i, disks[d].getData(i));
				
				// As we have loaded a new process, there may be idle CPUs
				// If that's the case, we remove the Dummy process and replace it
				//  with our new process.
				for(int i=0; i<ncpus; i++)
					if(cpuLists[i].getFront().getPID()==0)
					{
						cpuLists[i].popFront();
						runProcess( readyList.popFront(), i );
						break;
					}
			}
		}				
		else
		{				
			switch(disks[d].getError())
			{
				case 1: //disk.ERRORCODE_SOMETHING_WRONG:
					System.out.println("Error trying to read from disc: something went wrong!");
					break;
				case 2: //disk.ERRORCODE_ADDRESS_OUT_OF_RANGE:
					System.out.println("Error trying to read from disc: address out of range.");
					break;
				case 3: //disk.ERRORCODE_MISSING_EOF:
					System.out.println("Error trying to read from disc: missing EOF.");
					break;
			}
		}
		
		readyList.pushBack( paux );
		
		//make the disk run for the next request, if there are any
		if(!requests.isEmpty())
		{
			DiskRequest r = requests.poll();
			disks[r.disk].roda(r.op, r.add, r.data);
		}
	}
	
	// Each time the kernel runs it have access to all hardware components
	public void run(int interruptNumber, int cpu)
	{
		// Auxiliary variables
		ProcessDescriptor paux = null;
		FileDescriptor faux = null;
		int[] raux = null;
		
		System.err.println("Kernel called by CPU" + cpu + " for int " + interruptNumber);
		
		updateInterface(interruptNumber,cpu);
		
		// save context of this processor
		saveContext(cpu);
		
		switch(interruptNumber)
		{
			/////////////////////////
			// HARDWARE INTERRUPTS //
			/////////////////////////
			case 1:
				// ILLEGAL INSTRUCTION INT
				//
				System.out.println("Illegal/unknown instruction.");
				killCurrentProcess(cpu);
				break;
			
			case 2:
				// TIMER INT
				//
				handleTimerInt();
				break;
			
			case 3:
				// ILLEGAL MEMORY ACCESS
				//
				System.out.println("Illegal memory access!");
				killCurrentProcess(cpu);
				break;
			
			case 5:
				// DISK 1 INT
				//
			case 6:
				// DISK 2 INT
				//
				handleDiskInt(interruptNumber==5 ? 0 : 1);
				
				break;
			
			case 15:
				// CONSOLE INT
				//
				handleTerminal();
				
				break;
			
			/////////////////////////
			// SOFTWARE INTERRUPTS //
			/////////////////////////
			case 32:
				// EXIT	PROCESS INT
				//
				killCurrentProcess(cpu);
				break;
				
			case 34:
				// OPEN FILE INT
				//
				//TODO
				raux = procs[cpu].getReg();
				
				if( (raux[0]==0 || raux[0]==1) &&
					(raux[1]==0 || raux[1]==1))
				{
					paux = cpuLists[cpu].getFront();
					faux = paux.addFile(mem);
					faux.open( raux[0]==0 ? faux.FILEMODE_R:faux.FILEMODE_W, raux[1], raux[2]);
				}
				else
					System.out.println("Error opening file: invalid parameters.");
				
				break;
				
			case 35: // CLOSE
				//TODO
				raux = procs[cpu].getReg();
				paux = cpuLists[cpu].getFront();
				
				faux = paux.getFile(raux[0]);
				faux.close();
				paux.removeFile(raux[0]);
				
				break;
				
			case 36: // GET
				//TODO
				raux = procs[cpu].getReg();
				paux = readyList.getFront();
				
				faux = paux.getFile(raux[0]);
				raux = faux.get();
				procs[cpu].setReg(raux);
				
				break;
			
			case 37: // PUT
				//TODO
				break;
				
			case 46: // PRINT
				raux = procs[cpu].getReg();
				System.out.println( "PRINT: " + (raux[0]>>>24) + " " + ((raux[0]>>>16)&255) + " " + ((raux[0]>>>8)&255) + " " + (raux[0]&255));
				break;
				
			default:
				System.err.println("Unknown interrupt: " + interruptNumber);
		}
		// restore context of this processor
		restoreContext(cpu);
	}
}
