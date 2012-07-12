import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import javax.swing.*;


public class Drawer
{
	private static DrawingArea da;
	private static Kernel kernel;
	private static Map<String,Queue<Integer>> lists;
	int frameWidth, frameHeight;
	private int ncpus;
	
	// Interface layout constants
	private final int DISTY = 50;
	private final int INITX = 10;
	private final int INITBARSX = INITX+45;
	private final int INITY = 0;
	private final int INITBARSY = INITY-10;
	
	
    
    public void setKernel(Kernel k)
    {
    	kernel = k;
    }
    
    Drawer(int nc) 
    {
    	ncpus = nc;
    	lists = new HashMap<String,Queue<Integer>>();
    	
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
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        frameWidth = (int) (d.width * 0.8);
        frameHeight = (int) (DISTY*(3.5+ncpus));
        frame.setSize(frameWidth, frameHeight);
        frame.setLocation(d.width/2 - frameWidth/2, d.height - frameHeight);
        frame.setVisible(true);
    }
      
    public static void addToList(int PID, String name){
    	Queue<Integer> listaux = lists.get(name);
    	listaux.offer(PID);
	}
    
    public static void removeFromList(int PID, String name){
    	Queue<Integer> listaux = lists.get(name);
    	
		if( !listaux.isEmpty() )
			listaux.poll();
	}
    
    public static void addList(String name){
    	lists.put(name, new LinkedList<Integer>());
    }
    
    public static void tick()
    {
    	da.tick();
    }
    
    public static void drawEvent(int event, int cpu)
    {
    	da.drawEvent(event, cpu);
    }

    class DrawingArea extends JComponent
    {
    	BufferedImage image=null;
		Graphics2D g2d;
		private int x;
		private int[] lastDisk; 
		private int[] lastCpu;
    	
		
    	// Constructor
		public DrawingArea()
		{
			x=0;
			lastDisk = new int[2];
			for(int i=0; i<2; i++)
				lastDisk[i] = -1;
			lastCpu = new int[ncpus];
			for(int i=0; i<ncpus; i++)
				lastCpu[i] = -1;
		}
		
    	public void paintComponent(Graphics g)
		{
			super.paintComponent(g);

			//  Custom code to support painting from the BufferedImage
			if (image == null)
				createEmptyImage();

			g.drawImage(image, 0, 0, null);
		}
    	
    	public void drawEvent(int event, int cpu)
    	{
    		//if(event!=2)
    		{
    			g2d.setColor(Color.red);
    			int y = INITBARSY-5 + (cpu+1)*DISTY;
    			g2d.drawString(Integer.toString(event), INITBARSX+x-2, y);
    			g2d.drawLine(INITBARSX+x, y, INITBARSX+x, y+5);
    		}
    	}
    	
    	private void drawPID(int pid, int y)
    	{
    		g2d.setColor(Color.black);
			g2d.drawString(Integer.toString(pid), INITBARSX+x-2, y);
			g2d.drawLine(INITBARSX+x, y-20, INITBARSX+x, y-10);
    	}
    	
		public void tick()
		{	
			Queue<Integer> listaux = null;
			
			// draw reference bar
			g2d.setColor( Color.DARK_GRAY );
			g2d.fillRect(INITBARSX+x, 10, Config.XVAR, 1);
			g2d.drawLine(INITBARSX+x, 10, INITBARSX+x, 13);
			
			g2d.setColor( Color.black );
			int y = INITBARSY;

			// draw CPUs bars
			for(int i=0; i<ncpus; i++)
			{
				y+=DISTY;
				listaux = lists.get("CPU "+i);
				// idle cpu
				if(listaux.isEmpty()||listaux.peek()==0)
				{
					lastCpu[i] = -1;
					g2d.setColor(Color.gray);
				}			
				else
				{
					if(lastCpu[i]==-1 || lastCpu[i]!=listaux.peek())
					{
						// new process, draw label
						lastCpu[i]=listaux.peek();
						drawPID(lastCpu[i], y+30);
					}
					g2d.setColor(Color.black);					
				}
				g2d.fillRect(INITBARSX+x, y, Config.XVAR, 10);
			}

			// draw Disks bars
			for(int i=0; i<2; i++)
			{
				y+=DISTY;
				listaux = lists.get("Disk "+i);
				
				// idle disk
				if( listaux.isEmpty() )
				{
					g2d.setColor(Color.gray);
					lastDisk[i] = -1;
				}
				else
				{
					if(lastDisk[i]==-1 || lastDisk[i]!=listaux.peek())
					{
						// new process, draw label
						lastDisk[i]=listaux.peek();
						g2d.setColor(Color.black);
						drawPID(lastDisk[i], y+30);
					}
					g2d.setColor(Color.black);
				}
				g2d.fillRect(INITBARSX+x, y, Config.XVAR, 10);
			}
			
			//update the global x positioner
			x+=Config.XVAR;
			if(x+INITBARSX>frameWidth)
			{
				x = 0;
				g2d.clearRect(INITBARSX, 0, frameWidth, frameHeight); //clear bars area
				drawDisksSeparator();
			}
			
			repaint();
		}
    	
    	private void createEmptyImage()
		{
    		int y = INITY;
    		
    		image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
			g2d = (Graphics2D)image.getGraphics();
			g2d.setColor(Color.BLACK);
			g2d.setFont( new Font ("Arial", Font.BOLD, 14) );
			g2d.setBackground(Color.white);
			
			g2d.clearRect(0, 0, frameWidth, frameHeight);
			
			// cpu labels
			for(int i=0; i<ncpus; i++)
			{
				y+=DISTY;
				g2d.drawString("CPU " + i, INITX, y);
			}

			drawDisksSeparator();
			
			// disk labels
			g2d.setColor(Color.BLACK);
			y+=DISTY;
			g2d.drawString("Disk 0", INITX, y);
			y+=DISTY;
			g2d.drawString("Disk 1", INITX, y);
			
			//g2d.drawRect(y, y, width, height)
			
			g2d.setFont( new Font ("Arial", 0, 12) );
		}
    	
    	private void drawDisksSeparator()
    	{
    		int y = INITY + ncpus*DISTY + DISTY/2;
    		g2d.setColor(new Color(200,200,200));
			g2d.drawLine(30, y, frameWidth-30, y);
    	}
    }
}