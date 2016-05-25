package pro2.Plot;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;

public class GridLine {

	//================================================================================
    // Public Data
    //================================================================================
	public enum GridLineStyle {
		LINE,
	};
	//================================================================================
    // Private Data
    //================================================================================
	private GridLineStyle style;
	private Color color = new Color(0,0,0);
	private Point start = new Point (0,0);
	private Point end = new Point (0,0);

	//================================================================================
    // Constructors
    //================================================================================
	/**
	 * Create a gridline
	 * @param style: Style of the line
	 * @param color: Color of the line
	 * @param start: Start point
	 * @param end: End point
	 */
	public GridLine(GridLineStyle style, Color color, Point start, Point end) {
		this.style = style;
		this.color = color;
		this.start = start;
		this.end = end;
	}

	//================================================================================
    // Public Functions
    //================================================================================
	/**
	 * Paints the gridline
	 * @param g
	 */
	public void paint(Graphics g) {
		if(start.y != 188) {
			System.out.println("HERE");
		}
		Color col = g.getColor();
		g.setColor(this.color);
		g.drawLine(start.x, start.y, end.x, end.y);
		g.setColor(col);
	}

}
