import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
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
	private ArrayList< Queue<DiskRequest> > diskReqs;
	
	
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
		diskReqs = new ArrayList<Queue<DiskRequest>>();
		diskReqs.add( new LinkedList<DiskRequest>() );
		diskReqs.add( new LinkedList<DiskRequest>() );
		
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
				queueDiskRequest(disk, Disk.OPERATION_LOAD, initPos, 0);
			}
		}
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
	
	synchronized private void killCurrentProcess(int procId)
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
		ProcessDescriptor paux = cpuLists[cpu].getFront();
		if(paux!=null)
		{
			procs[cpu].setPC(paux.getPC());
			procs[cpu].setReg(paux.getReg());
		}
	}
	
	private void queueDiskRequest(int whichdisk, int operation, int address, int wdata)
	{
		if(diskReqs.get(whichdisk).isEmpty())
			disks[whichdisk].roda(operation, address, wdata);
		else
			diskReqs.get(whichdisk).add( new DiskRequest(whichdisk, operation, address, wdata) );
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
					queueDiskRequest(val[0], Disk.OPERATION_LOAD, val[1], 0);
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
	
	synchronized private void handleDiskInt(int d, int cpu)
	{
		ProcessDescriptor paux = null;
		FileDescriptor faux = null;
		int[] raux = procs[cpu].getReg();
		
		paux = diskLists[d].popFront();
		int flag = paux.getFlag();
		paux.resetFlag(); //reset hanging flag
		
		if(disks[d].getError()==Disk.ERRORCODE_SUCCESS)
		{
			// Disk attempt was SUCCESSFULL!
			//
			
			//process was being loaded from disk
			if(flag==ProcessDescriptor.FLAG_LOADING)
			{
				//write on memory the loaded data
				for(int i=0; i<disks[d].getSize(); i++)
					mem.superWrite(paux.getPartition()*mem.getPartitionSize() +i, disks[d].getData(i));
			}
			else if(flag == ProcessDescriptor.FLAG_OPEN) //process was opening a file for reading
			{
				faux = paux.getHangingFile();
				//update the effective size of the file
				faux.setSize( disks[d].getSize() );
				//set registers with information about the operation
				raux[1] = 0;
				raux[0] = faux.getId();
				//update the processor with the new registers data
				paux.setReg(raux);		
			}
			else if(flag == ProcessDescriptor.FLAG_PUT)
			{
				faux = paux.getHangingFile();
				if(faux.getPos() == faux.getSize()) //position was outside the original file location
					faux.incSize();
				faux.incPos();
				//set registers with information about the operation
				raux[1] = 0;
				//update the processor with the new registers data
				paux.setReg(raux);			
			}
			else if(flag == ProcessDescriptor.FLAG_GET)
			{
				faux = paux.getHangingFile();
				if(faux.getPos() < faux.getSize())
					faux.incPos();
				int data = disks[faux.getDisk()].getData(0);
				//set registers with information about the operation
				raux[0] = data;
				raux[1] = 0;
				//update the processor with the new registers data
				paux.setReg(raux);		
			}
		}
		else 
		{	
			// Disk attempt was UNSUCCESSFULL
			//
			
			switch(disks[d].getError())
			{
				case Disk.ERRORCODE_SOMETHING_WRONG:
					System.out.println("Error trying to read from disc: something went wrong!");
					break;
				case Disk.ERRORCODE_ADDRESS_OUT_OF_RANGE:
					System.out.println("Error trying to read from disc: address out of range.");
					break;
				case Disk.ERRORCODE_MISSING_EOF:
					System.out.println("Error trying to read from disc: missing EOF.");
					break;
			}

			if(flag == ProcessDescriptor.FLAG_OPEN) //process was opening a file for reading
			{
				//remove the file from the Files Table of the process
				paux.removeFile(paux.getHangingFile());
				//set registers with information about the operation
				raux[1] = 1;
				//update the processor with the new registers data
				paux.setReg(raux);
			}
			else if(flag == ProcessDescriptor.FLAG_PUT)
			{
				//set registers with information about the operation
				raux[1] = 1;
				//update the processor with the new registers data
				paux.setReg(raux);				
			}
		}
		
		// Done with all special handlings, put back this process for running
		readyList.pushBack( paux );
		
		// If we loaded a new process, there may be idle CPUs
		// If that's the case, we remove the Dummy process and replace it
		//  with our new process.
		if(flag==ProcessDescriptor.FLAG_LOADING)
			for(int i=0; i<ncpus; i++)
				if(cpuLists[i].getFront().getPID()==0)
				{
					cpuLists[i].popFront();
					runProcess( readyList.popFront(), i );
					break;
				}
		
		// Make the disk run for the next request, if there are any
		if(!diskReqs.get(d).isEmpty())
		{
			DiskRequest r = diskReqs.get(d).poll();
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
				handleDiskInt(interruptNumber==5 ? 0 : 1, cpu);
				
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
				raux = procs[cpu].getReg();
				
				if( (raux[0]==0 || raux[0]==1) &&
					(raux[1]==0 || raux[1]==1))
				{
					paux = cpuLists[cpu].getFront();
					int mode = raux[0]==0 ? FileDescriptor.FILEMODE_R:FileDescriptor.FILEMODE_W;
					faux = paux.openFile(mode, raux[1], raux[2]);
					if(mode == FileDescriptor.FILEMODE_R)
					{
						//set process flag for marking that it's waiting for a file operation
						paux.setFlag(ProcessDescriptor.FLAG_OPEN);
						//queue the disk request for opening the file
						queueDiskRequest(raux[1], Disk.OPERATION_LOAD, raux[2], 0 );
						//remove from CPU Queue and insert on Disk Queue
						diskLists[raux[1]].pushBack( cpuLists[cpu].popFront() );
						runProcess(readyList.popFront(),cpu);
						
						System.err.println("Requested for opening file on address " + raux[2] );
					}
				}
				else
					System.out.println("Error opening file: invalid parameters.");
				
				break;
				
			case 35: // CLOSE
				raux = procs[cpu].getReg();
				paux = cpuLists[cpu].getFront();
				
				faux = paux.getFile(raux[0]);				
				paux.removeFile(raux[0]);
				
				break;
				
			case 36: // GET
				raux = procs[cpu].getReg();
				paux = cpuLists[cpu].getFront();
				
				faux = paux.getFile(raux[0]);
				
				if(faux!=null && faux.getPos() < faux.getSize())
				{
					//set process flag for marking that it's waiting for a file operation
					paux.setFlag(ProcessDescriptor.FLAG_GET);
					paux.setHangingFile(faux);
					//queue the disk request
					queueDiskRequest(faux.getDisk(), Disk.OPERATION_READ, faux.getPos()+faux.getAddress(), 0 );
					//remove from CPU Queue and insert on Disk Queue
					diskLists[raux[1]].pushBack( cpuLists[cpu].popFront() );
					runProcess(readyList.popFront(),cpu);
					
					System.err.println("Requested for GET on file " + raux[0] );
				}
				else
				{
					System.err.println("ERROR!");
					//set registers with error data
					raux[1] = 1;
					if(faux==null) raux[0] = 1; //wrong file code
					else raux[0] = 0; //EOF code
					//update the processor with the new registers data
					procs[cpu].setReg(raux);
				}
				
				break;
			
			case 37: // PUT
				raux = procs[cpu].getReg();
				paux = cpuLists[cpu].getFront();
				
				faux = paux.getFile(raux[0]);
				
				if(faux!=null && faux.getPos()<faux.getSize()) //check if there's room in the file
				{
					//set process flag for marking that it's waiting for a file operation
					paux.setFlag(ProcessDescriptor.FLAG_PUT);
					paux.setHangingFile(faux);
					//queue the disk request
					queueDiskRequest(faux.getDisk(), Disk.OPERATION_WRITE, faux.getPos(), raux[1] );
					//remove from CPU Queue and insert on Disk Queue
					diskLists[raux[1]].pushBack( cpuLists[cpu].popFront() );
					runProcess(readyList.popFront(),cpu);
				}
				else
				{
					//set registers with error data
					raux[1] = 1;
					if(faux==null) raux[0] = 1;
					//update the processor with the new registers data
					procs[cpu].setReg(raux);
				}
				
				break;
				
			case 46: // PRINT
				raux = procs[cpu].getReg();
				System.out.println( "PRINT: " + (raux[0]>>>24) + " " + ((raux[0]>>>16)&255) + " " + ((raux[0]>>>8)&255) + " " + (raux[0]&255));
				break;
				
			default:
				System.out.println("Unknown interrupt: " + interruptNumber);
		}
		
		// restore context of this processor
		restoreContext(cpu);
	}
}
