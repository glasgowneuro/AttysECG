package tech.glasgowneuro.attysecg;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class LeadsView extends View {

    private final Paint textOKPaint;
    private final Paint textOffPaint;
    private final Paint legPaint;
    private final Rect bounds = new Rect();

    static final String right = "-,R";
    static final String left = "L,GND";
    static final String foot = "F,+";

    private boolean rOK = false;
    private boolean lOK = false;
    private boolean fOK = false;

    public LeadsView(Context context, AttributeSet attrs) {
        super(context,attrs);

        final float scale = getContext().getResources().getDisplayMetrics().density;

        textOKPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textOKPaint.setColor(0xff008000);
        textOKPaint.setTextSize(20 * scale);

        textOffPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textOffPaint.setColor(0xffff0000);
        textOffPaint.setTextSize(20 * scale);

        legPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legPaint.setColor(0xff808080);
        legPaint.setStrokeWidth(10);
    }

    public void setLeadStatus(boolean r, boolean l, boolean f) {
        rOK = r;
        lOK = l;
        fOK = f;
        invalidate();
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();

        int x0 = w/2;
        int dy = h/6;

        // body
        canvas.drawLine(x0,dy,x0,dy*4, legPaint);

        // arm
        canvas.drawLine(x0,dy*2, x0-dy, dy*3, legPaint);

        // arm
        canvas.drawLine(x0,dy*2, x0+dy, dy*3, legPaint);

        // leg
        canvas.drawLine(x0,dy*4, x0-dy, dy*5, legPaint);

        // leg
        canvas.drawLine(x0,dy*4, x0+dy, dy*5, legPaint);

        textOKPaint.getTextBounds(right, 0, right.length(), bounds);

        Paint okPaint;
        if (rOK) {
            okPaint = textOKPaint;
        } else {
            okPaint = textOffPaint;
        }
        canvas.drawText(right, x0 - dy - (float)bounds.width() / 2, dy * 3 + bounds.height(), okPaint);

        if (lOK) {
            okPaint = textOKPaint;
        } else {
            okPaint = textOffPaint;
        }
        canvas.drawText(left, x0+dy  - 1.1F * (float) bounds.width(), dy*3 + bounds.height(), okPaint);

        if (fOK) {
            okPaint = textOKPaint;
        } else {
            okPaint = textOffPaint;
        }
        // Draw the label text
        canvas.drawText(foot, x0+dy, dy*5, okPaint);

        // Draw the head
        canvas.drawCircle(x0, dy, (float)dy/2, legPaint);
    }


}
