package hex.schemas;

import hex.glm.GLM;
import hex.glm.GLMModel.GLMParameters;
import water.api.ModelParametersSchema;
import water.fvec.Frame;
import water.util.PojoUtils;

/**
 * Created by tomasnykodym on 8/29/14.
 */
public class GLMV2 extends ModelBuilderSchema<GLM,GLMV2,GLMV2.GLMParametersV2> {

  public static final class GLMParametersV2 extends ModelParametersSchema<GLMParameters, GLMParametersV2> {
    // TODO: parameters are all wrong. . .
    public String[] fields() { return new String[] { "destination_key", "max_iters", "normalize" }; }

    // Input fields
    public int max_iters;        // Max iterations
    public boolean normalize = true;

    public GLMParameters fillImpl(GLMParameters impl) {
      PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES);

      // Sigh:
      impl._train = (this.training_frame == null ? null : this.training_frame._key);
      impl._valid = (this.validation_frame == null ? null : this.validation_frame._key);

      return impl;
    }
  }

  //==========================
  // Custom adapters go here

  // Return a URL to invoke GLM on this Frame
  @Override protected String acceptsFrame( Frame fr ) { return "/v2/GLM?training_frame="+fr._key; }
}
