package hex.tree.gbm;

import hex.tree.gbm.GBMModel.GBMParameters.Family;
import org.junit.*;
import water.TestUtil;
import water.MRTask;
import water.fvec.Frame;
import water.fvec.Chunk;

import static org.junit.Assert.assertEquals;

public class GBMTest extends TestUtil {

  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  private abstract class PrepData { abstract int prep(Frame fr); }

  static final String ignored_aircols[] = new String[] { "DepTime", "ArrTime", "AirTime", "ArrDelay", "DepDelay", "TaxiIn", "TaxiOut", "Cancelled", "CancellationCode", "Diverted", "CarrierDelay", "WeatherDelay", "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsDepDelayed"};

  @Test public void testGBMRegression() {
    GBMModel gbm = null;
    Frame fr = null, fr2 = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._loss = Family.AUTO;
      parms._toEnum = false;     // Regression
      parms._response_column = fr._names[1]; // Row in col 0, dependent in col 1, predictor in col 2
      parms._ntrees = 1;
      parms._max_depth = 1;
      parms._min_rows = 1;
      parms._nbins = 20;
      // Drop Col 0 (row), keep 1 (response), keep col 2 (only predictor), drop remaining cols
      String[] xcols = parms._ignored_columns = new String[fr.numCols()-2];
      xcols[0] = fr._names[0];
      for( int i=3; i<fr.numCols(); i++ ) xcols[i-2] = fr._names[i];
      parms._learn_rate = 1.0f;
      parms._score_each_iteration=true;

      GBM job = null;
      try {
        job = new GBM(parms);
        gbm = job.trainModel().get();
      } finally {
        if (job != null) job.remove();
      }
      Assert.assertTrue(job._state == water.Job.JobState.DONE); //HEX-1817
      //Assert.assertTrue(gbm._output._state == Job.JobState.DONE); //HEX-1817

      // Done building model; produce a score column with predictions
      fr2 = gbm.score(fr);
      double sq_err = new CompErr().doAll(job.response(),fr2.vecs()[0])._sum;
      double mse = sq_err/fr2.numRows();
      assertEquals(79152.1233,mse,0.1);

    } finally {
      if( fr  != null ) fr .remove();
      if( fr2 != null ) fr2.remove();
      if( gbm != null ) gbm.delete();
    }
  }

  private static class CompErr extends MRTask<CompErr> {
    double _sum;
    @Override public void map( Chunk resp, Chunk pred ) {
      double sum = 0;
      for( int i=0; i<resp._len; i++ ) {
        double err = resp.at0(i)-pred.at0(i);
        sum += err*err;
      }
      _sum = sum;
    }
    @Override public void reduce( CompErr ce ) { _sum += ce._sum; }
  }

  @Test public void testBasicGBM() {
    // Regression tests
    basicGBM("./smalldata/junit/cars.csv",
             new PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }});

    // Classification tests
    basicGBM("./smalldata/junit/test_tree.csv",
             new PrepData() { int prep(Frame fr) { return 1; }
             });
    basicGBM("./smalldata/junit/test_tree_minmax.csv",
             new PrepData() { int prep(Frame fr) { return fr.find("response"); }
             });
    basicGBM("./smalldata/logreg/prostate.csv",
             new PrepData() { int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("CAPSULE"); }
             });
    basicGBM("./smalldata/junit/cars.csv",
             new PrepData() { int prep(Frame fr) { fr.remove("name").remove(); return fr.find("cylinders"); }
             });
    basicGBM("./smalldata/airlines/allyears2k_headers.zip",
             new PrepData() { int prep(Frame fr) {
               for( String s : ignored_aircols ) fr.remove(s).remove();
               return fr.find("IsArrDelayed"); }
             });
//    basicGBM("../datasets/UCI/UCI-large/covtype/covtype.data",
//             new PrepData() {
//               int prep(Frame fr) {
//                 assertEquals(581012,fr.numRows());
//                 // Covtype: predict on last column
//                 return fr.numCols()-1;
//               }
//             });
  }
//
//  @Test public void testBasicGBMFamily() {
//    Scope.enter();
//    // Classification with Bernoulli family
//    basicGBM("./smalldata/logreg/prostate.csv","prostate.hex",
//        new PrepData() {
//          int prep(Frame fr) {
//            assertEquals(380,fr.numRows());
//            // Remove patient ID vector
//            UKV.remove(fr.remove("ID")._key);
//            // Change CAPSULE and RACE to categoricals
//            Scope.track(fr.factor(fr.find("CAPSULE"))._key);
//            Scope.track(fr.factor(fr.find("RACE"   ))._key);
//            // Prostate: predict on CAPSULE
//            return fr.find("CAPSULE");
//          }
//        }, Family.bernoulli);
//    Scope.exit();
//  }
//
  // ==========================================================================
  public void basicGBM(String fname, PrepData prep) {
    basicGBM(fname, prep, false, Family.AUTO);
  }
//  public GBMModel basicGBM(String fname, PrepData prep, boolean validation) {
//    return basicGBM(fname, prep, validation, Family.AUTO);
//  }
  public void basicGBM(String fname, PrepData prep, Family family) {
    basicGBM(fname, prep, false, family);
  }
  public void basicGBM(String fname, PrepData prep, boolean validation, Family family) {
    GBMModel gbm = null;
    Frame fr = null, fr2= null;
    try {
      fr = parse_test_file(fname);
      int idx = prep.prep(fr); // hack frame per-test
      water.DKV.put(fr);       // Update frame after hacking it

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      if( idx < 0 ) { parms._toEnum = false; idx = ~idx; } else { parms._toEnum = true; }
      parms._train = fr._key;
      parms._response_column = fr._names[idx];
      parms._ntrees = 4;
      parms._loss = family;
      parms._max_depth = 4;
      parms._min_rows = 1;
      parms._nbins = 50;
      parms._learn_rate = .2f;
      parms._score_each_iteration=true;

      GBM job = null;
      try {
        job = new GBM(parms);
        gbm = job.trainModel().get();
      } finally {
        if (job != null) job.remove();
      }

      // Done building model; produce a score column with predictions
      fr2 = gbm.score(fr);

      Assert.assertTrue(job._state == water.Job.JobState.DONE); //HEX-1817
      //Assert.assertTrue(gbm._output._state == Job.JobState.DONE); //HEX-1817

    } finally {
      if( fr  != null ) fr .remove();
      if( fr2 != null ) fr2.remove();
      if( gbm != null ) gbm.delete();
    }
  }
//
//  // Test-on-Train.  Slow test, needed to build a good model.
//  @Test public void testGBMTrainTest() {
//    File file1 = TestUtil.find_test_file("smalldata/gbm_test/ecology_model.csv");
//    if( file1 == null ) return; // Silently ignore if file not found
//    Key fkey1 = NFSFileVec.make(file1);
//    Key dest1 = Key.make("train.hex");
//    File file2 = TestUtil.find_test_file("smalldata/gbm_test/ecology_eval.csv");
//    Key fkey2 = NFSFileVec.make(file2);
//    Key dest2 = Key.make("test.hex");
//    GBM gbm = new GBM();          // The Builder
//    GBM.GBMModel gbmmodel = null; // The Model
//    Frame ftest = null, fpreds = null;
//    try {
//      Frame fr = ParseDataset2.parse(dest1,new Key[]{fkey1});
//      UKV.remove(fr.remove("Site")._key); // Remove unique ID; too predictive
//      gbm.response = fr.vecs()[fr.find("Angaus")];   // Train on the outcome
//      gbm.source = fr;
//      gbm.ntrees = 5;
//      gbm.max_depth = 10;
//      gbm.learn_rate = 0.2f;
//      gbm.min_rows = 10;
//      gbm.nbins = 100;
//      gbm.invoke();
//      gbmmodel = UKV.get(gbm.dest());
//      testHTML(gbmmodel);
//      Assert.assertTrue(gbmmodel.get_params().state == Job.JobState.DONE); //HEX-1817
//
//      // Test on the train data
//      ftest = ParseDataset2.parse(dest2,new Key[]{fkey2});
//      fpreds = gbm.score(ftest);
//
//      // Build a confusion matrix
//      ConfusionMatrix CM = new ConfusionMatrix();
//      CM.actual = ftest;
//      CM.vactual = ftest.vecs()[ftest.find("Angaus")];
//      CM.predict = fpreds;
//      CM.vpredict = fpreds.vecs()[fpreds.find("predict")];
//      CM.invoke();               // Start it, do it
//
//      StringBuilder sb = new StringBuilder();
//      CM.toASCII(sb);
//      System.out.println(sb);
//
//    } finally {
//      gbm.source.delete(); // Remove the original hex frame key
//      if( ftest  != null ) ftest .delete();
//      if( fpreds != null ) fpreds.delete();
//      if( gbmmodel != null ) gbmmodel.delete(); // Remove the model
//      UKV.remove(gbm.response._key);
//      gbm.remove();           // Remove GBM Job
//    }
//  }
//
//  // Adapt a trained model to a test dataset with different enums
//  @Test public void testModelAdapt() {
//    File file1 = TestUtil.find_test_file("./smalldata/kaggle/KDDTrain.arff.gz");
//    Key fkey1 = NFSFileVec.make(file1);
//    Key dest1 = Key.make("KDDTrain.hex");
//    File file2 = TestUtil.find_test_file("./smalldata/kaggle/KDDTest.arff.gz");
//    Key fkey2 = NFSFileVec.make(file2);
//    Key dest2 = Key.make("KDDTest.hex");
//    GBM gbm = new GBM();
//    GBM.GBMModel gbmmodel = null; // The Model
//    try {
//      gbm.source = ParseDataset2.parse(dest1,new Key[]{fkey1});
//      gbm.response = gbm.source.vecs()[41]; // Response is col 41
//      gbm.ntrees = 2;
//      gbm.max_depth = 8;
//      gbm.learn_rate = 0.2f;
//      gbm.min_rows = 10;
//      gbm.nbins = 50;
//      gbm.invoke();
//      gbmmodel = UKV.get(gbm.dest());
//      testHTML(gbmmodel);
//      Assert.assertTrue(gbmmodel.get_params().state == Job.JobState.DONE); //HEX-1817
//
//      // The test data set has a few more enums than the train
//      Frame ftest = ParseDataset2.parse(dest2,new Key[]{fkey2});
//      Frame preds = gbm.score(ftest);
//      ftest.delete();
//      preds.delete();
//
//    } finally {
//      if( gbmmodel != null ) gbmmodel.delete(); // Remove the model
//      gbm.source.delete();      // Remove original hex frame key
//      UKV.remove(gbm.response._key);
//      gbm.remove();             // Remove GBM Job
//    }
//  }
//
//  // A test of locking the input dataset during model building.
//  @Test public void testModelLock() {
//    GBM gbm = new GBM();
//    try {
//      Frame fr = gbm.source = parseFrame(Key.make("air.hex"),"./smalldata/airlines/allyears2k_headers.zip");
//      for( String s : ignored_aircols ) UKV.remove(fr.remove(s)._key);
//      int idx =  fr.find("IsArrDelayed");
//      gbm.response = fr.vecs()[idx];
//      gbm.ntrees = 10;
//      gbm.max_depth = 5;
//      gbm.min_rows = 1;
//      gbm.nbins = 20;
//      gbm.cols = new int[fr.numCols()];
//      for( int i=0; i<gbm.cols.length; i++ ) gbm.cols[i]=i;
//      gbm.learn_rate = .2f;
//      gbm.fork();
//      try { Thread.sleep(100); } catch( Exception ignore ) { }
//
//      try {
//        fr.delete();            // Attempted delete while model-build is active
//        throw H2O.fail();       // Should toss IAE instead of reaching here
//      } catch( IllegalArgumentException ignore ) {
//      } catch( DException.DistributedException de ) {
//        assertTrue( de.getMessage().contains("java.lang.IllegalArgumentException") );
//      }
//
//      GBM.GBMModel model = gbm.get();
//      Assert.assertTrue(model.get_params().state == Job.JobState.DONE); //HEX-1817
//      if( model != null ) model.delete();
//
//    } finally {
//      if( gbm.source != null ) gbm.source.delete(gbm.self(),0.0f); // Remove original hex frame key
//      gbm.remove();             // Remove GBM Job
//    }
//  }
//
//  //  MSE generated by GBM with/without validation dataset should be same
//  @Test public void testModelMSEEqualityOnProstate() {
//    final PrepData prostatePrep =
//            new PrepData() {
//              @Override int prep(Frame fr) {
//                assertEquals(380,fr.numRows());
//                // Remove patient ID vector
//                UKV.remove(fr.remove("ID")._key);
//                // Prostate: predict on CAPSULE
//                return fr.find("CAPSULE");
//              }
//    };
//    double[] mseWithoutVal = basicGBM("./smalldata/logreg/prostate.csv","prostate.hex", prostatePrep, false).errs;
//    double[] mseWithVal    = basicGBM("./smalldata/logreg/prostate.csv","prostate.hex", prostatePrep, true ).errs;
//    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", mseWithoutVal, mseWithVal, 0.0001);
//  }
//
//  @Test public void testModelMSEEqualityOnTitanic() {
//    final PrepData titanicPrep =
//            new PrepData() {
//              @Override int prep(Frame fr) {
//                assertEquals(1309,fr.numRows());
//                // Airlines: predict on CAPSULE
//                return fr.find("survived");
//              }
//    };
//    double[] mseWithoutVal = basicGBM("./smalldata/titanicalt.csv","titanic.hex", titanicPrep, false).errs;
//    double[] mseWithVal    = basicGBM("./smalldata/titanicalt.csv","titanic.hex", titanicPrep, true ).errs;
//    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", mseWithoutVal, mseWithVal, 0.0001);
//  }

}
