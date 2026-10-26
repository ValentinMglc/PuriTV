package app.puritv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Region;
import android.view.View;

final class IconeMeteo extends View {
    private int code = 3;
    private boolean jour = true;
    private final Paint trait = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plein = new Paint(Paint.ANTI_ALIAS_FLAG);

    IconeMeteo(Context ctx) {
        super(ctx);
        trait.setStyle(Paint.Style.STROKE);
        trait.setStrokeCap(Paint.Cap.ROUND);
        trait.setStrokeJoin(Paint.Join.ROUND);
    }

    void regler(int code, boolean jour) {
        this.code = code;
        this.jour = jour;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        c.save();
        c.translate((getWidth() - s) / 2f, (getHeight() - s) / 2f);
        c.scale(s, s);
        trait.setStrokeWidth(0.065f);

        c.save();
        c.translate(0, .02f);
        couleur(0x40000000);
        dessiner(c);
        c.restore();
        couleur(Color.WHITE);
        dessiner(c);
        c.restore();
    }

    private void couleur(int argb) {
        trait.setColor(argb);
        plein.setColor(argb);
    }

    private void dessiner(Canvas c) {
        if (code == 0) {
            if (jour) soleil(c, .5f, .5f, .2f); else lune(c, .5f, .5f, .3f);
        } else if (code <= 2) {
            Path n = nuage(.58f, .62f, .74f);
            c.save();
            c.clipPath(bord(n), Region.Op.DIFFERENCE);
            if (jour) soleil(c, .36f, .36f, .14f); else lune(c, .38f, .36f, .2f);
            c.restore();
            c.drawPath(n, trait);
        } else if (code == 45 || code == 48) {
            c.drawLine(.18f, .36f, .82f, .36f, trait);
            c.drawLine(.12f, .52f, .76f, .52f, trait);
            c.drawLine(.26f, .68f, .88f, .68f, trait);
        } else if (code == 3) {
            c.drawPath(nuage(.5f, .56f, .86f), trait);
        } else {
            c.drawPath(nuage(.5f, .44f, .82f), trait);
            if (code >= 95) {
                Path eclair = new Path();
                eclair.moveTo(.54f, .58f);
                eclair.lineTo(.40f, .78f);
                eclair.lineTo(.50f, .78f);
                eclair.lineTo(.44f, .96f);
                eclair.lineTo(.62f, .72f);
                eclair.lineTo(.52f, .72f);
                eclair.lineTo(.58f, .58f);
                eclair.close();
                c.drawPath(eclair, plein);
            } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
                for (float x : new float[]{.32f, .5f, .68f}) {
                    c.drawCircle(x, .76f, .04f, plein);
                    c.drawCircle(x - .06f, .9f, .04f, plein);
                }
            } else {
                for (float x : new float[]{.34f, .5f, .66f}) c.drawLine(x, .72f, x - .07f, .9f, trait);
            }
        }
    }

    private void soleil(Canvas c, float cx, float cy, float r) {
        c.drawCircle(cx, cy, r, trait);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            c.drawLine(cx + dx * r * 1.55f, cy + dy * r * 1.55f, cx + dx * r * 2.05f, cy + dy * r * 2.05f, trait);
        }
    }

    private void lune(Canvas c, float cx, float cy, float r) {
        Path disque = new Path();
        disque.addCircle(cx, cy, r, Path.Direction.CW);
        Path ombre = new Path();
        ombre.addCircle(cx + r * .55f, cy - r * .4f, r * .82f, Path.Direction.CW);
        disque.op(ombre, Path.Op.DIFFERENCE);
        c.drawPath(disque, trait);
    }

    private static Path nuage(float cx, float cy, float w) {
        float x0 = cx - w / 2, h = w * .36f;
        Path p = new Path();
        p.addRoundRect(x0 + w * .04f, cy, x0 + w * .96f, cy + h, h / 2, h / 2, Path.Direction.CW);
        Path bosse = new Path();
        bosse.addCircle(x0 + w * .3f, cy + h * .1f, w * .2f, Path.Direction.CW);
        p.op(bosse, Path.Op.UNION);
        bosse.reset();
        bosse.addCircle(x0 + w * .56f, cy - h * .12f, w * .26f, Path.Direction.CW);
        p.op(bosse, Path.Op.UNION);
        bosse.reset();
        bosse.addCircle(x0 + w * .78f, cy + h * .22f, w * .16f, Path.Direction.CW);
        p.op(bosse, Path.Op.UNION);
        return p;
    }

    private Path bord(Path nuage) {
        Paint marge = new Paint(trait);
        marge.setStrokeWidth(.16f);
        Path contour = new Path();
        marge.getFillPath(nuage, contour);
        contour.op(nuage, Path.Op.UNION);
        return contour;
    }
}
