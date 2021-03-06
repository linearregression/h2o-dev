package hex;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.TransfVec;
import water.fvec.Vec;
import water.util.ArrayUtils;
import static water.util.PrettyPrint.printConfusionMatrix;
import java.util.Arrays;

/**
 *  Compare two categorical columns, reporting a grid of co-occurrences.
 *  <br>
 *  The semantics follows R-approach - see R code:
 *  <pre>
 *  &gt; l = c("A", "B", "C")
 *  &gt; a = factor(c("A", "B", "C"), levels=l)
 *  &gt; b = factor(c("A", "B", "A"), levels=l)
 *  &gt; confusionMatrix(a,b)
 *
 *            Reference
 * Prediction A B C
 *          A 1 0 0
 *          B 0 1 0
 *          C 1 0 0
 *  </pre>
  *
 *  <p>Note: By default we report zero rows and columns.</p>
 *
 *  @author cliffc
 */
public class ConfusionMatrix extends Iced {
  public Frame actual;
  public Vec vactual; // Column of the actual results (will display vertically)
  public Frame predict;
  public Vec vpredict; // Column of the predicted results (will display horizontally)
  String [] actual_domain;      // domain of the actual response
  String [] predicted_domain;   // domain of the predicted response
  private String [] domain;     //  union of domains
  public long cm[][]; // Confusion Matrix (or co-occurrence matrix
  public double mse = Double.NaN;  //Mean Squared Error

  private boolean classification;

  private void init() throws IllegalArgumentException {
    // Input handling
    if( vactual==null || vpredict==null )
      throw new IllegalArgumentException("Missing actual or predict!");
    if (vactual.length() != vpredict.length())
      throw new IllegalArgumentException("Both arguments must have the same length!");

    classification = vactual.isInt() && vpredict.isInt();

    // Handle regression kind which is producing CM 1x1 elements
    if (!classification && vactual.isEnum())
      throw new IllegalArgumentException("Actual vector cannot be categorical for regression scoring.");
    if (!classification && vpredict.isEnum())
      throw new IllegalArgumentException("Predicted vector cannot be categorical for regression scoring.");
  }

  public void execImpl() {
    init();
    Vec va = null,vp = null, avp = null;
    try {
      if (classification) {
        // Create a new vectors - it is cheap since vector are only adaptation vectors
        va = vactual .toEnum(); // always returns TransfVec
        actual_domain = va.domain();
        vp = vpredict.toEnum(); // always returns TransfVec
        predicted_domain = vp.domain();
        if (!Arrays.equals(actual_domain, predicted_domain)) {
          domain = ArrayUtils.domainUnion(actual_domain, predicted_domain);
          int[][] vamap = Model.getDomainMapping(domain, actual_domain, true);
          va = TransfVec.compose( (TransfVec) va, vamap, domain, false ); // delete original va
          int[][] vpmap = Model.getDomainMapping(domain, predicted_domain, true);
          vp = TransfVec.compose( (TransfVec) vp, vpmap, domain, false ); // delete original vp
        } else domain = actual_domain;
        // The vectors are from different groups => align them, but properly delete it after computation
        if (!va.group().equals(vp.group())) {
          avp = vp;
          vp = va.align(vp);
        }
        cm = new CM(domain.length).doAll(va,vp)._cm;
      } else {
        mse = new CM(1).doAll(vactual,vpredict).mse();
      }
      return;
    } finally {       // Delete adaptation vectors
      if (va!=null) DKV.remove(va._key);
      if (vp!=null) DKV.remove(vp._key);
      if (avp!=null) DKV.remove(avp._key);
    }
  }

  // Compute the co-occurrence matrix
  private static class CM extends MRTask<CM> {
    /* @IN */ final int _c_len;
    /* @OUT Classification */ long _cm[][];
    /* @OUT Regression */ private double mse() { return _count > 0 ? _mse/_count : Double.POSITIVE_INFINITY; }
    /* @OUT Regression Helper */ private double _mse;
    /* @OUT Regression Helper */ private long _count;
    CM(int c_len) { _c_len = c_len;  }
    @Override public void map( Chunk ca, Chunk cp ) {
      //classification
      if (_c_len > 1) {
        _cm = new long[_c_len+1][_c_len+1];
        int len = Math.min(ca._len,cp._len); // handle different lenghts, but the vectors should have been rejected already
        for( int i=0; i < len; i++ ) {
          int a=ca.isNA0(i) ? _c_len : (int)ca.at80(i);
          int p=cp.isNA0(i) ? _c_len : (int)cp.at80(i);
          _cm[a][p]++;
        }
        if( len < ca._len )
          for( int i=len; i < ca._len; i++ )
            _cm[ca.isNA0(i) ? _c_len : (int)ca.at80(i)][_c_len]++;
        if( len < cp._len )
          for( int i=len; i < cp._len; i++ )
            _cm[_c_len][cp.isNA0(i) ? _c_len : (int)cp.at80(i)]++;
      } else {
        _cm = null;
        _mse = 0;
        assert(ca._len == cp._len);
        int len = ca._len;
        for( int i=0; i < len; i++ ) {
          if (ca.isNA0(i) || cp.isNA0(i)) continue; //TODO: Improve
          final double a=ca.at0(i);
          final double p=cp.at0(i);
          _mse += (p-a)*(p-a);
          _count++;
        }
      }
    }

    @Override public void reduce( CM cm ) {
      if (_cm != null && cm._cm != null) {
        ArrayUtils.add(_cm,cm._cm);
      } else {
        assert(! Double.isNaN(_mse) && ! Double.isNaN(cm._mse));
        assert(_cm == null && cm._cm == null);
        _mse += cm._mse;
        _count += cm._count;
      }
    }
  }

  private boolean toHTML( StringBuilder sb ) {
    //TODO: Re-enable
//    if (classification) {
//      DocGen.HTML.section(sb,"Confusion Matrix");
//      if( cm == null ) return true;
//      printConfusionMatrix(sb, cm, domain, true);
//    } else{
//      DocGen.HTML.section(sb,"Mean Squared Error");
//      if( mse == Double.NaN ) return true;
//      DocGen.HTML.arrayHead(sb);
//      sb.append("<tr class='warning'><td>" + mse + "</td></tr>");
//      DocGen.HTML.arrayTail(sb);
//    }
//    return true;
    throw H2O.unimpl();
  }

  public void toASCII( StringBuilder sb ) {
    if (classification) {
      if(cm == null) return;
      printConfusionMatrix(sb, cm, domain, false);
    } else {
      sb.append("MSE: " + mse);
    }
  }
}
