import java.util.*;
import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

class SopaInterface {
	/**
	 * @author Guilherme Peretti Pezzi
	 *
	 * 1st version of Sopa interface (05/10/05)
	 * Uses static fields and methods to minimize changes on the simulator code
	 * Send comments, suggestions and bugs to pezzi@inf.ufrgs.br
	 * 
	 * 
	 */

	// display stuff
	private static GridBagConstraints c;
	private static Container pane ;
	private static JFrame jFrame; 
	private static JPanel jContentPane ;
	private static JPanel pane2 ;
	private static JTextArea jTextArea;
	private static JButton playButton, stepButton, stopButton;
	private static JScrollPane scrollPane = null;
	// used to display lists
	private static HashMap myJLists = new HashMap();
	// used to keep lists data
	private static HashMap myLinkedLists = new HashMap();
	//	last grid x used for dynamic lists display
	private static int lastInsPos = 0; 
	private static GlobalSynch gs;


	//	PUBLIC METHODS	
	// initializes interface
	public static void initViewer(GlobalSynch gsynch) {
		gs =gsynch;

		//fru fru java
//		JFrame.setDefaultLookAndFeelDecorated(true);
		
		//Create and set up the window.
		jFrame = getJFrame();
		jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		//Set up the content pane.
		pane = jFrame.getContentPane();
		pane.setLayout(new GridBagLayout());
		c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH; //HORIZONTAL;
		addFixComponentsToPane();
		
		//Display the window.
		jFrame.pack();
		jFrame.setVisible(true);
	}

	// dynamicaly creates process list on jframe 
	// when a new list is created    
	public static void addList(String name){
		//Init lists    	
		myJLists.put(name, new JList() );
		myLinkedLists.put(name, new LinkedList() );

		//GridBag parameters
		c.gridwidth = 1; 
		c.ipadx = 10;
		c.ipady = 0;
		c.gridy = 0;
		c.weighty=1;
		c.gridx = lastInsPos;
		c.insets = new Insets(0,0,0,0);
		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.NORTH;

		//adding label
		JLabel titulo = new JLabel(name);
		pane.add(titulo,c);

		//adding list
		c.gridy = 1;
		pane2 = new JPanel();
		pane2.setLayout(new BoxLayout(pane2, BoxLayout.PAGE_AXIS));
		pane2.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
		pane2.setPreferredSize(new Dimension(20,160));
		pane2.add( (JList) myJLists.get(name) );
		pane.add(pane2, c);

		//increments x counter and repack frame
		lastInsPos++; 
		jFrame.pack();
	}

	//update text display
	public static void updateDisplay(int proc, int disk, int i)
	{
		appendMsg("\n"+ "      " + Integer.toString(proc) + "\t      " +
				Integer.toString(disk) +"\t      " + 
				Integer.toString(i));
	}
	
	//adds element to list and redisplay list 
	public static void addToList(int PID, String name){
		LinkedList changedList = ( (LinkedList) myLinkedLists.get(name));

		//update linked list
		changedList.add( Integer.toString(PID) );
		
		//update screen structure 
		( (JList) myJLists.get(name) ).setListData(changedList.toArray());
		jFrame.pack();
	}

	//	removes element from list and redisplay list
	public static void removeFromList(int PID, String name){
		LinkedList changedList = ( (LinkedList) myLinkedLists.get(name));
		int test = -1;
		if( !changedList.isEmpty() ){
			test = Integer.parseInt( (String) changedList.removeFirst() );
			if ( test != PID ) 
			{
				appendMsg("<INTERFACE> ERROR REMOVING " + PID + " FROM LIST: "+ name  + "\n" );
			}else;
		}else{
			appendMsg("<INTERFACE> ERROR REMOVING FROM EMPTY LIST: " +name  + "\n" );
		}
		( (JList) myJLists.get(name) ).setListData(changedList.toArray());    	
		jFrame.pack();
	}

	// PRIVATE METHODS   
	/**
	 * This method initializes jFrame	
	 * 	
	 * @return javax.swing.JFrame	
	 */
	private static JFrame getJFrame() {
		if (jFrame == null) {
			jFrame = new JFrame();
			jFrame.setSize(new java.awt.Dimension(400,400));
			jFrame.setContentPane(getJContentPane());
		}
		return jFrame;
	}

	/**
	 * This method initializes jContentPane	
	 * 	
	 * @return javax.swing.JPanel	
	 */
	private static JPanel getJContentPane() {
		if (jContentPane == null) {
			jContentPane = new JPanel();
			jContentPane.setLayout(new BorderLayout());
		}
		return jContentPane;
	}

	// adds time control buttons and text output
	private static void addFixComponentsToPane() {

		// LINE 3
		c.gridy = 2;

		c.ipadx = 0;
		c.ipady = 5;
		c.gridwidth = 3;      
		c.insets = new Insets(5,10,5,10);

		playButton = new JButton("Play");
		playButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent ae){
				gs.play();                         
				}});
		c.gridx = 0;
		pane.add(playButton, c);

		stopButton = new JButton("Pause");
		stopButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent ae){
				gs.pause();
				}});
		c.gridx = 3;
		pane.add(stopButton, c);

		stepButton = new JButton("Next Step");
		stepButton.addActionListener(new ActionListener(){
				public void actionPerformed(ActionEvent ae){
				gs.advance();                            
				}});
		c.gridx = 6;
		pane.add(stepButton, c);      

		//		LINE 4
		c.gridy = 3;

		jTextArea = new JTextArea(" Process \t   Disk \t Interrupt");
		scrollPane = new JScrollPane(jTextArea);
		scrollPane.setPreferredSize(new Dimension(100,300));
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		c.insets = new Insets(5,10,5,10);
		c.weighty = 1.0;   //request any extra vertical space
		c.anchor = GridBagConstraints.PAGE_END; //bottom of space
		c.ipady = 0;
		c.gridx = 0;       
		c.gridwidth = 9;   //9 columns wide
		pane.add(scrollPane, c);

	}

	private static void appendMsg(String msg){
		jTextArea.setText(jTextArea.getText()+msg);
		jTextArea.setCaretPosition(jTextArea.getText().length());
	}
}
