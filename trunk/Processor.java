class Processor extends Thread
{
	// Access to hardware components
	private IntController hint;
	private GlobalSynch synch;
	private MMU mmu;
	
	// CPU internal components
	private int PC;	// Program Counter
	private int[] IR;	// Instruction Register
	private int[] reg;	// general purpose registers
	private int[] flag;   // flags Z E L
	private int id;
	private final int Z = 0;
	private final int E = 1;
	private final int L = 2;
	public Semaphore sem;

	// Access methods
	synchronized public int getPC() { return PC; }
	synchronized public void setPC(int i) { PC = i; }
	synchronized public int[] getReg() { return reg; }
	synchronized public void setReg(int[] r) { reg = r; }
	synchronized public int[] getFlag() { return flag; }
	synchronized public void setFlag(int[] f) { flag = f; }

	// Kernel is like a software in ROM
	private Kernel kernel;
	
	public MMU getMMU() { return mmu; }
	
	public Processor(int _id, IntController i, GlobalSynch gs, Memory m, ConsoleListener c, 
			Timer t, Disk d1, Disk d2, Kernel k)
	{
		id = _id;
		hint = i;
		synch = gs;
		kernel = k;
		PC = 0;
		IR = new int[4];
		reg = new int[16];
		flag = new int[3];
		mmu = new MMU(m,i);
		sem = new Semaphore(1);
	}
	
	public void run()
	{
		while (true)
		{
			// sleep a tenth of a second
			synch.mysleep(2);
			
			sem.P();
			
			// read from memory in the address indicated by PC
			int RD = mmu.read(PC);
			// break the 32bit word into 4 separate bytes
			IR[0] = RD>>>24;
			IR[1] = (RD>>>16) & 255;
			IR[2] = (RD>>>8) & 255;
			IR[3] = RD & 255;
			// print CPU status to check if it is ok
			System.err.print("CPU " + id + ": PC=" + PC);
			//System.err.print(" IR="+IR[0]+" "+IR[1]+" "+IR[2]+" "+IR[3]+" ");
			System.err.print(" REGS="+reg[0]+" "+reg[1]+" "+reg[2]+" "+reg[3]+" ");
			
			PC = PC+1;
			
			// Execute basic instructions of the architecture
			execute_basic_instructions();
			
			sem.V();
			
			//System.err.println("PC on CPU " + id + " is " + PC + " after executing the basic instructions.");

			// Check for Hardware Interrupt and if so call the kernel
			int thisInt = hint.getAndReset();
			if ( thisInt != 0)
				kernel.run(thisInt,id);
		}
	}

	public void execute_basic_instructions()
	{
		if ((IR[0]=='L') && (IR[1]=='M'))
		{
			System.err.println(" [L M r m] ");
			reg[IR[2]] = mmu.read(IR[3]);
		}
		else
			if ((IR[0]=='L') && (IR[1]=='C'))
			{
				System.err.println(" [L C r c] ");
				reg[IR[2]] = IR[3];
			}
			else
				if ((IR[0]=='W') && (IR[1]=='M'))
				{
					System.err.println(" [W M r m] ");
					mmu.write(IR[3],reg[IR[2]]);
				}
				else
					if ((IR[0]=='S') && (IR[1]=='U'))
					{
						System.err.println(" [S U r1 r2] ");
						reg[IR[2]] = reg[IR[2]] - reg[IR[3]];
					}
					else
						if ((IR[0]=='A') && (IR[1]=='D'))
						{
							System.err.println(" [A D r1 r2] ");
							reg[IR[2]] = reg[IR[2]] + reg[IR[3]];
						}
						else
							if ((IR[0]=='D') && (IR[1]=='E') && (IR[2]=='C'))
							{
								System.err.println(" [D E C r1] ");
								reg[IR[3]] = reg[IR[3]] - 1;
							}
							else
								if ((IR[0]=='I') && (IR[1]=='N') && (IR[2]=='C'))
								{
									System.err.println(" [I N C r1] ");
									reg[IR[3]] = reg[IR[3]] + 1;
								}
								else
									if ((IR[0]=='C') && (IR[1]=='P'))
									{
										System.err.println(" [C P r1 r2] ");
										if (reg[IR[2]] == 0) flag[Z] = 1; else flag[Z] = 0;
										if (reg[IR[2]] == reg[IR[3]]) flag[E] = 1; else flag[E] = 0;
										if (reg[IR[2]] < reg[IR[3]]) flag[L] = 1; else flag[L] = 0;
									}
									else
										if ((IR[0]=='J') && (IR[1]=='P') && (IR[2]=='A'))
										{
											System.err.println(" [J P A m] ");
											PC = IR[3];
										}
										else
											if ((IR[0]=='J') && (IR[1]=='P') && (IR[2]=='Z'))
											{
												System.err.println(" [J P Z m] ");
												if (flag[Z] == 1)
													PC = IR[3];
											}
											else
												if ((IR[0]=='J') && (IR[1]=='P') && (IR[2]=='E'))
												{
													System.err.println(" [J P E m] ");
													if (flag[E] == 1)
														PC = IR[3];
												}
												else
													if ((IR[0]=='J') && (IR[1]=='P') && (IR[2]=='L'))
													{
														System.err.println(" [J P L m] ");
														if (flag[L] == 1)
															PC = IR[3];
													}
													else
														if (IR[0]=='I'&&IR[1]=='N'&&IR[2]=='T')
														{
															System.err.println(" [I N T n] ");
															sem.V();
															kernel.run(IR[3], id);
															sem.P();
													
														}
														else
														{
															System.err.println(" [? ? ? ?] ");
															sem.V();
															kernel.run(1, id);
															sem.P();
														}
	}
}
