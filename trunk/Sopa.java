///////////////////////////////////////////////////////////////////
//
// Sistema Operacional Para Avaliacao - Marcelo Johann - 20/06/2002
//
// SOPA820061 - All hardware components for the 2006-1 edition
//
// This code was updated to include synchronization between threads
// so that we can implement step by step execution as well as an
// interface with Play, Pause and Step-by-step commands. 
//
//  Please, consider that some testing and tuning may be required.
//
///////////////////////////////////////////////////////////////////

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import javax.swing.*;

public class Sopa {
	private final static int NCPU = 2;
	private final static int NPARTITIONS = 6;
	
	public static void main(String args[]) {
		// The program models a complete computer with most HW components
		// The kernel, which is the software component, might have been
		// created here also, but Processor has a reference to it and it
		// has a reference to the processor, so I decided that all software
		// is under the processor environment: kernel inside processor.

		GlobalSynch globalSynch = new GlobalSynch(500); // quantum of X ms
		IntController intController = new IntController();

		// Create interface
		SopaInterface.initViewer(globalSynch);
		
		// Create graphics
		Drawer drawer = new Drawer(NCPU);

		// Create window console
		ConsoleWindow mw = new ConsoleWindow();
		mw.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		ConsoleListener console = mw.getListener();
		console.setInterruptController(intController);
		console.setGlobalSynch(globalSynch);

		Memory mem = new Memory(intController, 128, NPARTITIONS);
		Timer timer = new Timer(intController, globalSynch);
		Disk disk1 = new Disk(0,intController, globalSynch, mem, 1024,"disk.txt");
		Disk disk2 = new Disk(1,intController, globalSynch, mem, 1024,"disk.txt");
		
		Kernel kernel = new Kernel(intController,mem,console, timer,disk1,disk2, NCPU, NPARTITIONS);
		
		drawer.setKernel(kernel);
		
		Processor[] procs = new Processor[NCPU];
		for(int i=0; i<NCPU; i++)
			procs[i] = new Processor(i,intController, globalSynch, mem,
				console, timer, disk1, disk2, kernel);
		
		kernel.init(procs);

		// start all threads
		for(int i=0; i<NCPU; i++)
			procs[i].start();
		timer.start();
		disk1.start();
		disk2.start();
		globalSynch.start();
	}
}

class GlobalSynch extends Thread {
	// This is a master clock for the simulation. Instead of running concurrent
	// threads with the normal sleep from Java, we use instead this GlobalSynch
	// sleep system that can be controlled and executed step by step.
	private int quantum;
	private boolean stepMode;
	private Semaphore lock;

	public GlobalSynch(int q) {
		quantum = q;
		stepMode = false;
		lock = new Semaphore(1);
	}

	public synchronized void mysleep(int n) {
		for (int i = 0; i < n; ++i)
			try {
				wait();
			} catch (InterruptedException e) {
			}
	}

	public synchronized void mywakeup() {
		notifyAll();
	}

	public void run() {
		while (true) {
			lock.P();
			if (stepMode == false)
				lock.V();
			try {
				sleep(quantum);
			} catch (InterruptedException e) {
			}
			mywakeup();
		}
	}

	public synchronized void advance() {
		if (stepMode == true)
			lock.V();
	}

	public synchronized void pause() {
		if (stepMode == false) {
			stepMode = true;
			lock.P();
		}
	}

	public synchronized void play() {
		if (stepMode == true) {
			stepMode = false;
			lock.V();
		}
	}
}

class ConsoleWindow extends JFrame {
	private ConsoleListener ml;
	private JTextField line;

	public ConsoleWindow() {
		super("Console");
		Container c = getContentPane();
		c.setLayout(new FlowLayout());
		line = new JTextField(30);
		line.setEditable(true);
		c.add(line);
		ml = new ConsoleListener();
		line.addActionListener(ml);
		ml.setTextField(line);
		setSize(400, 80);
		setVisible(true);
	}

	public ConsoleListener getListener() {
		return ml;
	}
}

class ConsoleListener implements ActionListener {
	// Console is an intelligent terminal that reads an entire command
	// line and then generates an interrupt. It should provide a method
	// for the kernel to read the command line.

	private IntController hint;
	private GlobalSynch synch;
	private Semaphore sem;
	private SlaveListener sl;
	private String l;
	private JTextField line;

	public void setTextField(JTextField tx) { line = tx; }
	
	public void setInterruptController(IntController i) {
		hint = i;
		sem = new Semaphore(0);
		sl = new SlaveListener(i);
		sl.start();
	}

	public void setGlobalSynch(GlobalSynch gs) {
		synch = gs;
	}

	public void actionPerformed(ActionEvent e) {
		l = e.getActionCommand();
		line.setText("");

		// Here goes the code that generates an interrupt
		sl.setInterrupt();
	}

	synchronized public String getLine() {
		return l;
	}
}

class SlaveListener extends Thread {
	private IntController hint;
	private Semaphore sem;

	public SlaveListener(IntController i) {
		hint = i;
		sem = new Semaphore(0);
	}

	public void setInterrupt() {
		sem.V();
	}

	public void run() {
		while (true) {
			sem.P();
			hint.set(15);
		}
	}
}

class Semaphore {
	// This class was found on the Internet and had some bugs fixed.
	// It implements a semaphore using the Java built in monitors.
	// Note how the primitives wait and notify are used inside the
	// monitor, and make the process executing on it leave the
	// monitor until another event happens.
	int value;

	public Semaphore(int initialValue) {
		value = initialValue;
	}

	public synchronized void P() {
		while (value <= 0) {
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
		value--;
	}

	public synchronized void V() {
		value++;
		notify();
	}
}

class IntController {
	// The interrupt controller component has a private semaphore to maintain
	// interrupt requests coming from all other components.
	// Interruptions from memory are exceptions that need to be handled right
	// now, and have priority over other Ints. So, memory interrupt has its
	// own indicator, and the others compete among them using the Semaphore.
	
	// An additional semaphore is used for concurrent access by multiple
	// processors.

	private int number;
	private Queue<Integer> numbers;
	private final int memoryInterruptNumber = 3;

	public IntController() {
		numbers = new LinkedList<Integer>();
		number = 0;
	}

	synchronized public void set(int n) {
		if (n == memoryInterruptNumber)
			number = n;
		else {
			numbers.offer(n);
		}
	}

	synchronized public int getAndReset() {
		int ret;
		
		if (number > 0)
		{
			ret = number;
			number = 0;
		}
		else
		{
			if(numbers.size()==0)
				ret = 0;
			else
				ret = numbers.remove();
		}
		
		return ret;
	}

	/*public void reset(int n) {
		if (n == memoryInterruptNumber)
			number = 0;
		else {
			numbers.remove();
		}
	}*/
}

class Timer extends Thread {
	// Our programmable timer. This is the OLD version that used to make
	// interrupts to inform about the end of a CPU slice. It's supposed to be
	// programmable. But has some weaknesses (bugs) that make it not fare.
	// IN 2006-1, you are asked to use simple versions that just place
	// an interrupt at each time interval and the kernel itself must
	// count these timer ticks and test for a the time slice end.
	private IntController hint;
	private GlobalSynch synch;

	public Timer(IntController i, GlobalSynch gs) {
		hint = i;
		synch = gs;
	}

	// This is the thread that keeps track of time and generates the
	// interrupts
	public void run() {
		while (true) {
			synch.mysleep(2);
			Drawer.tick();
			System.err.println("tick!");
			hint.set(2);
		}
	}
}

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
	public int tickTime() { System.err.println("Process "+PID+" ticked "+time); return --time; }
	
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
