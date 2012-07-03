package us.jsy.map;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;

import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class CrossOverlay extends Overlay {
	public Paint paint = new Paint();;
	private Point center;
	private int d = 5;

	public CrossOverlay() {
		paint.setColor(Color.BLACK);
	}
		
	/* (non-Javadoc)
	 * @see com.google.android.maps.Overlay#draw(android.graphics.Canvas, com.google.android.maps.MapView, boolean)
	 */
	@Override
	public void draw(Canvas canvas, MapView mapView, boolean shadow) {
		super.draw(canvas, mapView, shadow);
		
		if (shadow)
			return;
		
		Projection proj = mapView.getProjection();
		center = proj.toPixels(mapView.getMapCenter(), center);
		
		canvas.drawLine(center.x-d, center.y, center.x+d+1, center.y, paint);
		canvas.drawLine(center.x, center.y-d, center.x, center.y+d+1, paint);
	}
}
