import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedList;

import javax.swing.*;


public class Drawer
{
	private static DrawingArea da;
	private static Kernel kernel;
	private static HashMap myLinkedLists = new HashMap();
	private int ncpus;
    
    public void setKernel(Kernel k)
    {
    	kernel = k;
    }
    
    Drawer(int nc) 
    {
    	ncpus = nc;
    	
        // Create a frame
        JFrame frame = new JFrame();

        frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
        
        // Add a component with a custom paint method
        da = new DrawingArea();
        frame.getContentPane().add(da);

        // Display the frame
        int frameWidth = 600;
        int frameHeight = 300;
        frame.setSize(frameWidth, frameHeight);
        frame.setVisible(true);
    }
    
    public static void addList(String name)
    {
    	myLinkedLists.put(name, new LinkedList() );
    }
    
    public static void addToList(int PID, String name){
		LinkedList changedList = ( (LinkedList) myLinkedLists.get(name));

		//update linked list
		changedList.add( Integer.toString(PID) );
		
		//update screen structure 
		( (JList) myJLists.get(name) ).setListData(changedList.toArray());
		window.pack();
	}
    
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
		window.pack();
	}
    
    public static void tick()
    {
    	da.tick();
    }

    class DrawingArea extends JComponent
    {
    	BufferedImage image=null;
		Graphics2D g2d;
		private int x;
		
    	// Constructor
		public DrawingArea()
		{
			setBackground(Color.WHITE);
			
			x = 0;
		}
		
		public void tick()
		{
			g2d.setColor( Color.BLACK );
			g2d.drawRect(x, 10, 1, 10);
			++x;
			
			repaint();
		}
    	
    	public void paintComponent(Graphics g)
		{
			super.paintComponent(g);

			//  Custom code to support painting from the BufferedImage
			if (image == null)
			{
				createEmptyImage();
			}

			g.drawImage(image, 0, 0, null);
		}
    	
    	private final int DISTY = 30;
    	private final int INITX = 10;
    	
    	private void createEmptyImage()
		{
			image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
			g2d = (Graphics2D)image.getGraphics();
			g2d.setColor(Color.BLACK);
			
			int y = 15;
			for(int i=0; i<ncpus; i++)
			{
				y+=DISTY;
				g2d.drawString("CPU " + i, INITX, y);
			}

			g2d.drawString("Disc 1", INITX, y+=DISTY);
			g2d.drawString("Disc 2", INITX, y+=DISTY);
		}
    }
}