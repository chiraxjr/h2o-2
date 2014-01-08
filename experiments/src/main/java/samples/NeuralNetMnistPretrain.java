package samples;


import hex.Layer;
import hex.Layer.*;
import hex.MnistCanvas;
import hex.Trainer;
import water.fvec.Vec;

import javax.swing.*;

public class NeuralNetMnistPretrain extends NeuralNetMnist {
  public static void main(String[] args) throws Exception {
    Class job = Class.forName(Thread.currentThread().getStackTrace()[1].getClassName());
    samples.launchers.CloudLocal.launch(job, 1);
  }

  @Override protected Layer[] build(Vec[] data, Vec labels, VecsInput inputStats, VecSoftmax outputStats) {
    Layer[] ls = new Layer[4];
    ls[0] = new VecsInput(data, inputStats);
//    ls[1] = new Layer.RectifierDropout(1024);
//    ls[2] = new Layer.RectifierDropout(1024);
    ls[1] = new Layer.Tanh(500);
    ls[2] = new Layer.Tanh(500);
    ls[3] = new VecSoftmax(labels, outputStats);
    for( int i = 0; i < ls.length; i++ ) {
      ls[i].initial_weight_distribution = Layer.InitialWeightDistribution.Uniform;
      ls[i].initial_weight_scale = 1;
      ls[i].rate = .01f;
      ls[i].rate_annealing = 1 / 1e6f;
      ls[i].momentum_start = .5f;
      ls[i].momentum_ramp = 60000 * 100;
      ls[i].momentum_stable = .99f;
      //ls[i].l1 = .005f;
      ls[i].init(ls, i);
      if (i>=1) ls[i].randomize(new java.util.Random(), 1.0f);
    }
    return ls;
  }

  @Override protected void startTraining(Layer[] ls) {
    preTrain(ls);

    //_trainer = new Trainer.Direct(ls, 0, self());
    _trainer = new Trainer.Threaded(ls, 0, self());
    //_trainer = new Trainer.MapReduce(ls, 0, self());

    _trainer.start();
  }

  protected void preTrain(Layer[] ls) {
    for( int i = 1; i < ls.length - 1; i++ ) {
      System.out.println("Pre-training level " + i);
      long time = System.nanoTime();
      preTrain(ls, i);
      System.out.println((int) ((System.nanoTime() - time) / 1e6) + " ms");
    }
  }

  protected void preTrain(Layer[] ls, int index) {
    // Build a network with same layers below 'index', and an auto-encoder at the top
    Layer[] pre = new Layer[index + 2];
    VecsInput input = (VecsInput) ls[0];
    pre[0] = new VecsInput(input.vecs, input);
    for( int i = 1; i < index; i++ ) {
      //pre[i] = new Layer.Rectifier(ls[i].units);
      pre[i] = new Layer.Tanh(ls[i].units);
      pre[i].rate = 0;
      Layer.shareWeights(ls[i], pre[i]);
    }

    // Auto-encoder is a layer and a reverse layer on top
    //pre[index] = new Layer.Rectifier(ls[index].units);
    pre[index] = new Layer.Tanh(ls[index].units);
    pre[index].rate = .00001f;
    //pre[index + 1] = new Layer.RectifierPrime(ls[index - 1].units);
    pre[index + 1] = new Layer.TanhPrime(ls[index - 1].units);
    pre[index + 1].rate = .00001f;
    Layer.shareWeights(ls[index], pre[index]);
    Layer.shareWeights(ls[index], pre[index + 1]);
    for( int i = 0; i < pre.length; i++ ) {
      pre[i].init(pre, i, false, 0, null);
    }

    _trainer = new Trainer.Direct(pre, 10, self());

    // Basic visualization of images and weights
    JFrame frame = new JFrame("H2O");
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    MnistCanvas canvas = new MnistCanvas(_trainer);
    frame.setContentPane(canvas.init());
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);

    _trainer.start();
    _trainer.join();
  }
}
