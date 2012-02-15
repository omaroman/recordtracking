/**
 * Author: OMAROMAN
 * Date: 10/28/11
 * Time: 1:31 PM
 */

package play.modules.recordtracking.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Mask {}

