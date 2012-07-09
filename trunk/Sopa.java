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

import javax.swing.*;

public class Sopa {
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

		// Create window console
		MyWin mw = new MyWin();
		mw.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		ConsoleListener console = mw.getListener();
		console.setInterruptController(intController);
		console.setGlobalSynch(globalSynch);

		Memory mem = new Memory(intController, 128, 8);
		Timer timer = new Timer(intController, globalSynch);
		Disk disk1 = new Disk(0,intController, globalSynch, mem, 1024,"disk.txt");
		Disk disk2 = new Disk(1,intController, globalSynch, mem, 1024,"disk.txt");
		
		Processor processor = new Processor(intController, globalSynch, mem,
				console, timer, disk1, disk2);

		// start all threads
		processor.start();
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

class MyWin extends JFrame {
	private ConsoleListener ml;
	private JTextField line;

	public MyWin() {
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

	public String getLine() {
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

	private Semaphore semhi;
	private int number[] = new int[2];
	private final int memoryInterruptNumber = 3;

	public IntController() {
		semhi = new Semaphore(1);
	}

	public void set(int n) {
		if (n == memoryInterruptNumber)
			number[0] = n;
		else {
			semhi.P();
			number[1] = n;
		}
	}

	public int get() {
		if (number[0] > 0)
			return number[0];
		else
			return number[1];
	}

	public void reset(int n) {
		if (n == memoryInterruptNumber)
			number[0] = 0;
		else {
			number[1] = 0;
			semhi.V();
		}
	}
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
	private int counter = 0;
	private int slice = 5;

	public Timer(IntController i, GlobalSynch gs) {
		hint = i;
		synch = gs;
	}

	// For the services below, time is expressed in tenths of seconds
	public void setSlice(int t) {
		slice = t;
	}

	public void setTime(int t) {
		counter = t;
	}

	public int getTime() {
		return counter;
	}

	// This is the thread that keeps track of time and generates the
	// interrupt when a slice has ended, but can be reset any time
	// with any "time-to-alarm"
	public void run() {
		while (true) {
			counter = slice;
			while (counter > 0) {
				synch.mysleep(2);
				--counter;
				System.err.println("tick " + counter);
			}
			System.err.println("timer INT");
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
	
	public boolean 	isLoading() { return isloading; }
	public void 	setLoaded() { isloading = false; }
	
	public int 		getPID() { return PID; }
	public int 		getPC() { return PC; }
	public void 	setPC(int i) { PC = i; }
	public int[] 	getReg() { return reg; }
	public void 	setReg(int[] r) { reg = r; }
	public int 		getPartition() { return partition; }
	public void 	setPartition(int p) { partition = p; }
	
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
	}
	

}

// This list implementation (and the 'next filed' in ProcessDescriptor) was
// programmed in a class to be faster than searching Java's standard lists,
// and it matches the names of the C++ STL. It is all we need now...

class ProcessList {
	private String myName = "No name";
	private ProcessDescriptor first = null;
	private ProcessDescriptor last = null;

	public ProcessDescriptor getFront() {
		return first;
	}

	public ProcessDescriptor getBack() {
		return last;
	}

	public ProcessList(String name) {
		myName = name;
		SopaInterface.addList(myName);
	}

	public ProcessDescriptor popFront() {
		ProcessDescriptor n;
		if (first != null) {
			n = first;
			first = first.getNext();
			if (last == n)
				last = null;
			n.setNext(null);

			// Update interface
			SopaInterface.removeFromList(n.getPID(), myName);

			return n;
		}
		return null;
	}

	public void pushBack(ProcessDescriptor n) {
		n.setNext(null);
		if (last != null)
			last.setNext(n);
		else
			first = n;
		last = n;

		// Update interface
		SopaInterface.addToList(n.getPID(), myName);
	}
}
