package com.jackleg.EventFinding;

public class Point {
	private Double x;
	private Double y;
	
	public Point(double x, double y) {
		this.x = x;
		this.y = y;
	}

	/**
	 * @return the x
	 */
	public double getX() {
		return x;
	}

	/**
	 * @return the y
	 */
	public double getY() {
		return y;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(x).append("\t").append(y);

		return sb.toString();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		Point point = (Point)obj;
		double threshold = 1e-15;
		
		if(Math.abs(x-point.getX()) < threshold
			&& Math.abs(y-point.getY()) < threshold
		)
			return true;
		
		return false;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return (x.hashCode() + y.hashCode())/2;
	}	
}
