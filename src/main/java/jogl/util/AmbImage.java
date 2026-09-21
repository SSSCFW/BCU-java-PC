package jogl.util;

import common.system.fake.FakeGraphics;
import common.system.fake.FakeImage;
import jogl.GLStatic;
import utilpc.awt.FIBI;

import javax.imageio.ImageIO;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.*;
import java.util.function.Supplier;

public class AmbImage implements FakeImage {

	private Supplier<InputStream> stream;
	private final File file;
	private final AmbImage par;
	private final int[] cs;
	private boolean force, failed;

	private FIBI bimg;
	private GLImage gl;

	protected AmbImage(BufferedImage b) {
		stream = null;
		file = null;
		par = null;
		cs = null;
		bimg = (FIBI) FIBI.build(b);
		force = true;
	}

	protected AmbImage(File f) {
		stream = null;
		file = f;
		par = null;
		cs = null;
	}

	protected AmbImage(Supplier<InputStream> sup) {
		stream = sup;
		file = null;
		par = null;
		cs = null;
	}

	private AmbImage(AmbImage img, int... c) {
		stream = null;
		file = null;
		par = img;
		cs = c;
	}

	@Override
	public BufferedImage bimg() {
		checkBI();
		if (bimg == null)
			return null;
		return bimg.bimg();
	}

	@Override
	public int getHeight() {
		check();
		if(bimg!=null&&bimg.bimg()!=null)return bimg.getHeight();
		if(gl!=null)return gl.getHeight();
		return 1;
	}

	@Override
	public int getRGB(int i, int j) {
		checkBI();
		return bimg!=null&&bimg.bimg()!=null?bimg.getRGB(i,j):0;
	}

	@Override
	public FakeImage getSubimage(int i, int j, int k, int l) {
		return new AmbImage(this, i, j, k, l);
	}

	@Override
	public int getWidth() {
		check();
		if(bimg!=null&&bimg.bimg()!=null)return bimg.getWidth();
		if(gl!=null)return gl.getWidth();
		return 1;
	}

	@Override
	public Object gl() {
		checkGL();
		return gl;
	}

	@Override
	public boolean isValid() {
		if (failed) return false;
		check();
		return (bimg != null && bimg.bimg() != null) || gl != null;
	}

	@Override
	public void mark(Marker str) {

		if (str == Marker.UNI)
			forceBI();
		if (str == Marker.BG) {
			checkBI();
			if (bimg!=null&&bimg.bimg()!=null&&bimg.bimg().getWidth() % 4 != 0)
				force = true;
		}
		if (str == Marker.EDI)
			forceBI();
		if (str == Marker.RECOLOR)
			checkBI();
		if (str == Marker.RECOLORED) {
			// TODO if graphics is faster?
			ByteArrayOutputStream abos = new ByteArrayOutputStream();
			try {
				ImageIO.write(bimg(), "PNG", abos);
			} catch (IOException e) {
				e.printStackTrace();
			}
			force = false;
			stream = () -> new ByteArrayInputStream(abos.toByteArray());
			gl = null;

		}
	}

	@Override
	public void setRGB(int i, int j, int p) {
		forceBI();
		if(bimg!=null&&bimg.bimg()!=null)bimg.setRGB(i,j,p);
	}

	@Override
	public void unload() {
	}

	@Override
	public FakeImage cloneImage() {
		AmbImage copy;

		if(bimg != null && bimg.bimg()!=null) {
			BufferedImage ori = bimg.bimg();

			ColorModel cm = ori.getColorModel();
			boolean alphaMulti = ori.isAlphaPremultiplied();
			WritableRaster wr = ori.copyData(null);

			copy = new AmbImage(new BufferedImage(cm, wr, alphaMulti, null));
		} else if(file != null) {
			copy = new AmbImage(file);
		} else if(stream != null) {
			copy = new AmbImage(stream);
		} else if(par != null && cs != null) {
			copy = new AmbImage(par, cs.clone());
		} else {
			copy = null;
		}

		return copy;
	}

	@Override
	public FakeGraphics getGraphics() {
		if(bimg != null)
			return bimg.getGraphics();
		else if(gl != null)
			return gl.getGraphics();
		else
			return null;
	}

	private void check() {
		if (gl != null || bimg != null)
			return;
		if (!GraphicsEnvironment.isHeadless() && (GLGraphics.count > 0 || GLStatic.ALWAYS_GLIMG))
			checkGL();
		if (gl == null)
			checkBI();
	}

	private void checkBI() {
		if ((bimg != null && bimg.bimg()!=null) || failed)
			return;
		try {
			if (stream != null)
				bimg = (FIBI) FIBI.builder.build(stream);
			else if (file != null)
				bimg = (FIBI) FIBI.builder.build(file);
			else if(par!=null&&cs!=null) {
				par.checkBI();
				if(par.bimg!=null&&par.bimg.bimg()!=null)
					bimg = par.bimg.getSubimage(cs[0], cs[1], cs[2], cs[3]);
			}
			if (bimg == null || bimg.bimg()==null) {
				bimg=null;
				failed = true;
			}
		} catch (Exception e) {
			bimg=null;failed=true;
			System.err.println("BCU image load failed: "+e.getClass().getSimpleName()+": "+e.getMessage());
		}
	}

	private void checkGL() {
		if (gl != null || failed)
			return;
		try {
			if (force) {
				checkBI();
				if(bimg==null||bimg.bimg()==null)return;
				gl = GLImage.build(bimg.bimg());
			} else if (stream != null)
				gl = GLImage.build(stream.get());
			else if (file != null)
				gl = GLImage.build(file);
			else if(par!=null&&cs!=null) {
				par.checkGL();
				if (par.gl != null)
					gl = par.gl.getSubimage(cs[0], cs[1], cs[2], cs[3]);
			}
			if(gl==null&&bimg==null)failed=true;
		} catch(Exception e) {
			gl=null;failed=true;
			System.err.println("BCU GL image load failed: "+e.getClass().getSimpleName()+": "+e.getMessage());
		}
	}

	private void forceBI() {
		checkBI();
		force = bimg != null && bimg.bimg() != null;
		gl = null;
	}
}
