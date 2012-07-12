import java.io.*;

class Disk extends Thread
{
	// Our disc component has a semaphore to implement it's dependency on
	// a processor's call. The semaphore is private, and we offer 
	// a method "roda" that unlocks it. Does it need to be synchronized???
	// It needs a semaphore to avoid busy waiting, but...
	private IntController hint;
	private GlobalSynch synch;
	private Semaphore sem;
	private String fileName;
	private int[] diskImage;
	private int diskSize;
	private int id;

	// Disk interface registers
	private int address;
	private int writeData;
	private int[] readData;
	private int readSize;
	private int operation;
	private int errorCode;

	// Some codes to get the meaning of the interface.
	// You can use the codes inside the kernel, like: Disk.OPERATION_READ
	public final static int OPERATION_READ = 0;
	public final static int OPERATION_WRITE = 1;
	public final static int OPERATION_LOAD = 2;
	public final static int ERRORCODE_SUCCESS = 0;
	public final static int ERRORCODE_SOMETHING_WRONG = 1;
	public final static int ERRORCODE_ADDRESS_OUT_OF_RANGE = 2;
	public final static int ERRORCODE_MISSING_EOF = 3;
	public final static int BUFFER_SIZE = 128;
	public final static int END_OF_FILE = 0xFFFFFFFF;

	// Constructor
	public Disk(int n, IntController i, GlobalSynch gs, int s, String name)
	{
		id = n;
		hint = i;
		synch = gs;
		sem = new Semaphore(0);
		// remember size and create disk memory
		diskSize = s;
		fileName = name;
		diskImage = new int[s];
		readData = new int[BUFFER_SIZE];
		readSize = 0;
	}

	// Methods that the kernel (in CPU) should call: "roda" activates the disk
	// The last parameter, data, is only for the 'write' operation
	public void roda(int op, int add, int data)
	{
		address = add;
		writeData = data;
		readSize = 0;
		operation = op;
		errorCode = ERRORCODE_SUCCESS;
		sem.V();
	}
	
	// After disk traps an interruption, kernel retrieve its results
	public int getError() { return errorCode; }
	public int getSize() { return readSize; }
	public int getData(int buffer_position) { return readData[buffer_position]; }

	// The thread that is the disk itself
	public void run()
	{
		try { load(fileName); } catch (IOException e){ System.err.println("Coudln't initialize disk " + id); }
		while (true)
		{
			// wait for some request coming from the processor
			sem.P();
			// Processor requested: now I have something to do!
			
			// Do some turns to simulate a real disk
			int turns = (int) (Config.minTurns + Math.random()*(Config.maxTurns-Config.minTurns));
			for (int i=0; i < turns; ++i)
			{
				synch.mysleep(1);
				//System.err.println("disk made a turn");
			}

			if (address < 0 || address >= diskSize)
				errorCode = ERRORCODE_ADDRESS_OUT_OF_RANGE;
			else
			{
				errorCode = ERRORCODE_SUCCESS;

				switch(operation)
				{
					case OPERATION_READ:
						System.err.println("OPERATION_READ");
						readSize = 1;
						readData[0] = diskImage[address];
						break;
					case OPERATION_WRITE:
						System.err.println("OPERATION_WRITE");
						diskImage[address] = writeData;
						break;
					case OPERATION_LOAD:
						System.err.println("OPERATION_LOAD");
						int diskIndex = address;
						int bufferIndex = 0;
						while (diskImage[diskIndex] != END_OF_FILE)
						{
							System.err.println(".");
							readData[bufferIndex] = diskImage[diskIndex];
							++diskIndex;
							++bufferIndex;
							if (bufferIndex >= BUFFER_SIZE || diskIndex >= diskSize)
							{
								errorCode = ERRORCODE_MISSING_EOF;
								break;
							}
						}
						readSize = bufferIndex;
						break;
				}
			}		

			// generate the interrupt
			if(id==0)
				hint.set(5);
			else
				hint.set(6);
		}
	}

	// this is to read disk initial image from a hosted text file
	private void load(String filename) throws IOException
	{
		FileReader f = new FileReader(filename);
		StreamTokenizer tokemon = new StreamTokenizer(f);
		int bytes[] = new int[4];
		int tok = tokemon.nextToken();
		for (int i=0; tok != StreamTokenizer.TT_EOF && (i < diskSize); ++i)
		{
			for (int j=0; tok != StreamTokenizer.TT_EOF && j<4; ++j)
			{
				if (tokemon.ttype == StreamTokenizer.TT_NUMBER )
					bytes[j] = (int) tokemon.nval;
				else
					if (tokemon.ttype == StreamTokenizer.TT_WORD )
						bytes[j] = (int) tokemon.sval.charAt(0); 
					else
						System.out.println("Unexpected token at disk image!"); 
				tok = tokemon.nextToken();
			}
			diskImage[i] = ((bytes[0]&255)<<24) | ((bytes[1]&255)<<16) | 
				((bytes[2]&255)<<8) | (bytes[3]&255);
			System.out.println("Parsed "+bytes[0]+" "+bytes[1]+" "
					+bytes[2]+" "+bytes[3]+" = "+diskImage[i]);
		}
	}
}
