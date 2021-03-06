package hex;

import hex.schemas.ModelBuilderSchema;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;

abstract public class SupervisedModelBuilder<M extends SupervisedModel<M,P,O>, P extends SupervisedModel.SupervisedParameters, O extends SupervisedModel.SupervisedOutput> extends ModelBuilder<M,P,O> {

  protected transient Vec _response; // Handy response column
  public Key _response_key; // Handy response column
  public Vec response() { return _response == null ? (_response = DKV.get(_response_key).<Vec>get()) : _response; }

  protected transient Vec _vresponse; // Handy validation response column
  public Key _vresponse_key; // Handy response column
  public Vec vresponse() { return _vresponse == null ? (_vresponse = DKV.get(_vresponse_key).<Vec>get()) : _vresponse; }

  public int _nclass; // Number of classes; 1 for regression; 2+ for classification
  public final boolean isClassifier() { return _nclass > 1; }

  /** Constructor called from an http request; MUST override in subclasses. */
  public SupervisedModelBuilder(P parms) { super(parms);  /*only call init in leaf classes*/ }
  public SupervisedModelBuilder(String desc, P parms) { super(desc,parms);  /*only call init in leaf classes*/ }
  public SupervisedModelBuilder(Key dest, String desc, P parms) { super(dest,desc,parms);  /*only call init in leaf classes*/ }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
   *
   *  Validate the response column; move it to the end; flip it to an Enum if
   *  requested.  Validate the max_after_balance_size; compute the number of
   *  classes.   */
  @Override public void init(boolean expensive) {
    super.init(expensive);
    if( _parms._max_after_balance_size <= 0.0 )
      error("_max_after_balance_size","Max size after balancing needs to be positive, suggest 1.0f");

    if( _train == null ) return; // Nothing more to check
    if( _train.numCols() <= 1 )
      error("_train", "Training data must have at least 2 features (incl. response).");

    // put response to the end (if not already), and convert to an enum
    int ridx = _train.find(_parms._response_column);
    if( ridx == -1 ) // Actually, think should not get here either (cutout at higher layer)
      error("_response_column", "Response column " + _parms._response_column + " not found in frame: " + _parms.train() + ".");
    _response = _train.remove(ridx);
    _vresponse= _valid.remove(ridx);
    if( _response.isBad() ) 
      error("_response_column", "Response column is all NAs!");
    if( _response.isConst() ) 
      error("_response_column", "Response column is constant!");
    if( _parms._toEnum && expensive ) { // Expensive; only do it on demand
       _response=  _response.toEnum();
      _vresponse= _vresponse.toEnum();
    }
    _train.add(_parms._response_column,  _response);
    _valid.add(_parms._response_column, _vresponse);
     _response_key =  _response._key;
    _vresponse_key = _vresponse._key;

    // #Classes: 1 for regression, domain-length for enum columns
    _nclass = _response.isEnum() ? _response.domain().length : 1;
  }    
}

