/**
 * Author: OMAROMAN
 * Date: 10/28/11
 * Time: 1:23 PM
 */
package play.modules.recordtracking;

import javassist.*;
import javassist.bytecode.*;
import net.parnassoft.playutilities.EnhancerUtility;

import javassist.bytecode.annotation.Annotation;

import play.classloading.ApplicationClasses.ApplicationClass;
import play.classloading.enhancers.Enhancer;

import play.Logger;
import play.modules.recordtracking.annotations.Mask;
import play.modules.recordtracking.annotations.NoTracking;
import play.modules.recordtracking.interfaces.Trackable;

import javax.persistence.*;
import java.util.Collections;
import java.util.List;

public class RecordTrackingEnhancer extends Enhancer {

    private CtClass ctClass;
    private ConstPool constPool;

	@Override
	public void enhanceThisClass(ApplicationClass appClass) throws Exception {

        // Initialize member fields
		ctClass = makeClass(appClass);
		constPool = ctClass.getClassFile().getConstPool();

		if (!isEnhanceableThisClass()) {
            return; // Do NOT enhance this class
        }

        createTrackDataField();
        createFillTrackDataMethod();
        createMethodFormatRecordTracking();
        addTrackableInterface();

//        createMethodOnPrePersist();
        createMethodOnPostPersist();
        createMethodOnPreRemove();
        createMethodOnPostRemove();
        createMethodOnPreUpdate();
        createMethodOnPostUpdate();

        Logger.debug("ENHANCED: %s.%s", ctClass.getPackageName(), ctClass.getName());

		// Done - Enhance Class.
		appClass.enhancedByteCode = ctClass.toBytecode();
		ctClass.defrost();
	}

    private boolean isEnhanceableThisClass() throws Exception {
        // Only enhance model classes.
        if (!EnhancerUtility.isAModel(classPool, ctClass)) {
            return false;
		}

		// Only enhance model classes with Entity annotation.
        if (!EnhancerUtility.isAnEntity(ctClass)) {
            return false;
        }

		// Skip enhance model classes if are annotated with @NoTracking
        if (isClassAnnotatedWithNoTracking()) {
            return false;
        }

        // Do enhance this class
        return true;
    }

    private boolean isClassAnnotatedWithNoTracking() throws Exception {
        return EnhancerUtility.hasAnnotation(ctClass, NoTracking.class.getName());
    }

    private boolean hasFormatRecordTrackingMethod() throws NotFoundException, ClassNotFoundException {
        // Property name
        final String methodName = "formatRecordTracking";
        try {
            CtMethod ctMethod = ctClass.getDeclaredMethod(methodName);
            Logger.debug("RETURN TYPE: %s", EnhancerUtility.methodReturnType(ctMethod));
            Logger.debug("DESCRIPTOR: %s", ctMethod.getMethodInfo().getDescriptor());
            return (ctMethod.getParameterTypes().length == 1 &&                                     // parameters
                    javassist.Modifier.isPublic(ctMethod.getModifiers()) &&
                    ctMethod.getLongName().endsWith("formatRecordTracking(java.lang.String)") &&    // signature
                    EnhancerUtility.methodReturnType(ctMethod).equals(String.class)); // return type
        } catch (NotFoundException e) {
            // Do Nothing... formatRecordTracking is not overridden
            return false;
        }
    }

    private void addTrackableInterface() {
        CtClass trackable = classPool.makeClass(Trackable.class.getName());
        ctClass.addInterface(trackable);
    }

    private void createTrackDataField() throws CannotCompileException {
        // Create track_data field
        String code = "java.util.Map track_data = new java.util.LinkedHashMap();";
        final CtField track_data = CtField.make(code, ctClass);
        ctClass.addField(track_data);

        // Annotate track_data with @Transient
        Annotation annotation = new Annotation(Transient.class.getName(), constPool);
        AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        attr.addAnnotation(annotation);
        track_data.getFieldInfo().addAttribute(attr);
    }

    /**
     * Get all transient fields and store them into track_data map
     * @throws CannotCompileException -
     * @throws ClassNotFoundException -
     * @throws NotFoundException -
     */
    private void createFillTrackDataMethod() throws CannotCompileException, ClassNotFoundException, NotFoundException {
        StringBuilder code = new StringBuilder();
        code.append("public void _fill_track_data() {");

        List<CtClass> mappedSupperClasses = EnhancerUtility.mappedSuperClassesUpToModel(ctClass);
        mappedSupperClasses.add(0, ctClass);    // add at first place
        Collections.reverse(mappedSupperClasses); // Reverse the order of fields

        code.append("String key = null;");
        code.append("String value = null;");

        // TODO: Find the field annotated with @Id (and its class in order to do the cast???)

        for (CtClass ctClass : mappedSupperClasses) {
            List<CtField> persistentFields = EnhancerUtility.getAllPersistentFields(ctClass);

            for (CtField ctField : persistentFields) {
                if (EnhancerUtility.isRelationship(ctField)) {
                    if (EnhancerUtility.isList(ctField) || EnhancerUtility.isSet(ctField)) {
                        // one-to-many (@OneToMany) or many-to-one (@ManyToOne) or many-to-many (@ManyToMany) association - A Collection (Set or List)
                        // Just get the id of every single object in the collection
                        code.append("key = \"@").append(ctField.getName()).append("_ids\";");
                        code.append("StringBuilder valueSB = new StringBuilder();");
                        code.append("if (").append(ctField.getName()).append(" != null) {");
                        code.append("for (java.util.Iterator i =").append(ctField.getName()).append(".iterator(); i.hasNext(); ) {");
                        code.append("play.db.jpa.Model model = (play.db.jpa.Model) i.next();"); // Cast to Model (or to What???)
                        code.append("if (model == null || model.id == null) {");
                        code.append("valueSB.append(\"null\").append(' ');");
                        code.append("} else {");
                        code.append("valueSB.append(model.id).append(' ');");
                        code.append("}");   // end if
                        code.append("}");   // end for
                        code.append("} else {");
                        code.append("valueSB.append(\"null\");");
                        code.append("}");   // end if
                        code.append("track_data.put(key, valueSB.toString());");
                    } else {    // one-to-one association (@OneToOne)
                        code.append("key = \"@").append(ctField.getName()).append("_id\";");
                        code.append("play.db.jpa.Model model = ").append("(play.db.jpa.Model)this.").append(ctField.getName()).append(";");
                        code.append("if (model == null || model.id == null) {");
                        code.append("value = \"null\";");
                        code.append("} else {");
                        code.append("value = model.id.toString();");
                        code.append("}");   // end if
                        code.append("track_data.put(key, value);");
                    }
                } else {  // Non-Relationship Field
                    if (EnhancerUtility.hasAnnotation(ctField, Mask.class.getName())) { // Mask Value
                        if (ctField.getType().isPrimitive()) {
                            // Create and instance wrapper of the primitive in order to invoke its toString method
                            code.append("value = org.apache.commons.lang.StringUtils.repeat(\"*\", ").append(buildNewPrimitiveWrapper(ctField)).append(".toString().length());");
                        } else {
                            code.append("if (").append("((").append(ctClass.getName()).append(")this).").append(ctField.getName()).append(" == null) {");
                            code.append("value = \"null\";");
                            code.append("} else {");
                            code.append("value = org.apache.commons.lang.StringUtils.repeat(\"*\", ((").append(ctClass.getName()).append(")this).").append(ctField.getName()).append(".toString().length());");
                            code.append("}");   // end if
                        }
                    } else {    // NO Mask Value
                        if (ctField.getType().isPrimitive()) {
                            // Create and instance wrapper of the primitive in order to invoke its toString method
                            code.append("value = (").append(buildNewPrimitiveWrapper(ctField)).append(").toString();");
                        } else {
                            code.append("if (").append("((").append(ctClass.getName()).append(")this).").append(ctField.getName()).append(" == null) {");
                            code.append("value = \"null\";");
                            code.append("} else {");
                            code.append("value = ((").append(ctClass.getName()).append(")this).").append(ctField.getName()).append(".toString();");
                            code.append("}");   // end if
                        }
                    }
                    code.append("key = \"@").append(ctField.getName()).append("\";");
                    code.append("track_data.put(key, value);");
                }
            }
        }

        // Print track_data, Uncomment the block just for debugging proposes
//        code.append("java.util.Iterator entries = track_data.entrySet().iterator();");
//        code.append("while (entries.hasNext()) {");
//        code.append("java.util.Map.Entry thisEntry = (java.util.Map.Entry) entries.next();");
//        code.append("String key = (String) thisEntry.getKey();");
//        code.append("String value = (String) thisEntry.getValue();");
//        code.append("play.Logger.debug(\"%s:%s\", new String[]{key, value});");
//        code.append("}");

        code.append("}");   // end method

//        Logger.debug(code.toString());    // print the injected code

        final CtMethod fillTrackData = CtMethod.make(code.toString(), ctClass);
        ctClass.addMethod(fillTrackData);
    }

    /**
     * Creates a method named formatRecordTracking
     * If the method already exists, skip it... it means the developer wrote his/her own implementation
     * @throws Exception -
     */
    private void createMethodFormatRecordTracking() throws Exception {
        // ----- Add formatRecordTracking() method -----

        if(hasFormatRecordTrackingMethod()) {
            Logger.debug("Skip creation of formatRecordTracking method for %s.%s", ctClass.getPackageName(), ctClass.getName());
            return;
        }
        Logger.debug("Creating formatRecordTracking method for %s.%s", ctClass.getPackageName(), ctClass.getName());

        List<CtClass> mappedSupperClasses = EnhancerUtility.mappedSuperClassesUpToModel(ctClass);
        mappedSupperClasses.add(0, ctClass);    // add at first place
        Collections.reverse(mappedSupperClasses);

        StringBuilder code = new StringBuilder();
        code.append("public String formatRecordTracking(String event) {");

        code.append("StringBuilder sb = new StringBuilder();");

        code.append("sb.append(\"-----[BEGIN]-----\");");   // Begin Delimiter
        code.append("sb.append(\"\\n\");"); // new line

        code.append("sb.append(event);");
        code.append("sb.append(\"\\n\");"); // new line

        code.append("sb.append(new java.util.Date());");
        code.append("sb.append(\"\\n\");"); // new line

        code.append("String key = play.modules.recordtracking.RecordTrackingProps.getSessionKey();");
        code.append("String user = null;");
        code.append("sb.append(\"User: \");");
        code.append("try {");
        code.append("user = play.mvc.Scope.Session.current().get(key);");
        code.append("if (user == null) {");
        code.append("sb.append(\"_UNKNOWN_\");");
        code.append("} else {");
        code.append("sb.append(user);");
        code.append("}");
        code.append("} catch(NullPointerException e){"); // via yml loading
        code.append("sb.append(\"_YAML_\");");
        code.append("}");
//        code.append("play.Logger.debug(\"----->SCOPE<-----\", null);");
        code.append("sb.append(\"\\n\");"); // new line

        code.append("sb.append(\"\\n\");"); // new line

        code.append("sb.append(\"<").append(ctClass.getName()).append(">\");");   // Model name
        code.append("sb.append(\"\\n\");"); // new line

        // The persistent fields...
        code.append("java.util.Set set = track_data.entrySet();");   // Get a set of the entries
        code.append("java.util.Iterator i = set.iterator(); ");   // Get an iterator
        code.append("while(i.hasNext()) {");
        code.append("java.util.Map.Entry me = (java.util.Map.Entry)i.next();");
        code.append("sb.append((String)me.getKey()).append(':');");
        code.append("sb.append((String)me.getValue());");
        code.append("sb.append(\"\\n\");"); // new line
        code.append("}");   // end while

        code.append("sb.append(\"-----[ END ]-----\");"); // End Delimiter
        code.append("sb.append(\"\\n\");"); // new line

        code.append("return sb.toString();");
        code.append("}");

//        Logger.debug(code.toString());

        final CtMethod formatRecordTracking = CtMethod.make(code.toString(), ctClass);
        ctClass.addMethod(formatRecordTracking);
    }

    private StringBuilder buildNewPrimitiveWrapper(CtField ctField) throws NotFoundException {
        Class primitiveWrapper = EnhancerUtility.primitiveWrapper(ctField);
        if (primitiveWrapper != null) {
            return new StringBuilder("new ").append(primitiveWrapper.getName()).append("(").append(ctField.getName()).append(")");
        } else {
            return new StringBuilder();
        }
    }

    private void createMethodOnPrePersist() throws Exception {
        // ----- Add onPrePersist() method -----

        // Check if there's a method annotated with @PreUpdate
		CtMethod methodWithPrePersistAnnot = EnhancerUtility.getMethodAnnotatedWith(ctClass, PrePersist.class.getName());

        StringBuilder code = new StringBuilder();
//        String debug = "play.Logger.debug(\"SCOPE -> %s.onPrePersist\", new String[]{this.getClass().getName()});";
        String info = ""; // Nothing to do

        if (methodWithPrePersistAnnot != null) {
//            code.append(debug);
            code.append(info);
            methodWithPrePersistAnnot.insertBefore(code.toString());
		} else {
            code.append("public void onPostPersist() { ");
//            code.append(debug);
            code.append(info);
            code.append("}");

//            Logger.debug("PostPersist code%n%s", code.toString());

            final CtMethod onPrePersist = CtMethod.make(code.toString(), ctClass);
            ctClass.addMethod(onPrePersist);

            Annotation annotation = new Annotation(PrePersist.class.getName(), constPool);
            AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
            attr.addAnnotation(annotation);

            onPrePersist.getMethodInfo().addAttribute(attr);
		}
    }

    private void createMethodOnPostPersist() throws Exception {
        // ----- Add onPostPersist() method -----

        // Check if there's a method annotated with @PreUpdate
		CtMethod methodWithPostPersistAnnot = EnhancerUtility.getMethodAnnotatedWith(ctClass, PostPersist.class.getName());

        StringBuilder code = new StringBuilder();
//        String debug = "play.Logger.debug(\"SCOPE -> %s.onPostPersist\", new String[]{this.getClass().getName()});";
        String info = "_fill_track_data(); play.modules.recordtracking.RecordTrackingLogger.getInstance().getLogger().info(formatRecordTracking(\"POST PERSIST\"));";

        if (methodWithPostPersistAnnot != null) {
//            code.append(debug);
            code.append(info);
            methodWithPostPersistAnnot.insertBefore(code.toString());
		} else {
            code.append("public void onPostPersist() { ");
//            code.append(debug);
            code.append(info);
            code.append("}");

//            Logger.debug("PostPersist code%n%s", code.toString());

            final CtMethod onPostPersist = CtMethod.make(code.toString(), ctClass);
            ctClass.addMethod(onPostPersist);

            Annotation annotation = new Annotation(PostPersist.class.getName(), constPool);
            AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
            attr.addAnnotation(annotation);

            onPostPersist.getMethodInfo().addAttribute(attr);
		}
    }

    private void createMethodOnPreRemove() throws Exception {
        // ----- Add onPreRemove() method -----

        // Check if there's a method annotated with @PreUpdate
		CtMethod methodWithPreRemoveAnnot = EnhancerUtility.getMethodAnnotatedWith(ctClass, PreRemove.class.getName());

        StringBuilder code = new StringBuilder();
//        String debug = "play.Logger.debug(\"SCOPE -> %s.onPreRemove\", new String[]{this.getClass().getName()});";
        String info = "_fill_track_data();";

        if (methodWithPreRemoveAnnot != null) {
//            code.append(debug);
            code.append(info);
            methodWithPreRemoveAnnot.insertBefore(code.toString());
		} else {
            code.append("public void onPreRemove() { ");
//            code.append(debug);
            code.append(info);
            code.append("}");

//            Logger.debug("PostRemove code%n%s", code.toString());

            final CtMethod onPreRemove = CtMethod.make(code.toString(), ctClass);
            ctClass.addMethod(onPreRemove);

            Annotation annotation = new Annotation(PreRemove.class.getName(), constPool);
            AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
            attr.addAnnotation(annotation);

            onPreRemove.getMethodInfo().addAttribute(attr);
        }
    }

    private void createMethodOnPostRemove() throws Exception {
        // ----- Add onPostRemove() method -----

        // Check if there's a method annotated with @PreUpdate
		CtMethod methodWithPostRemoveAnnot = EnhancerUtility.getMethodAnnotatedWith(ctClass, PostRemove.class.getName());

        StringBuilder code = new StringBuilder();
//        String debug = "play.Logger.debug(\"SCOPE -> %s.onPostRemove\", new String[]{this.getClass().getName()});";
        String info = "play.modules.recordtracking.RecordTrackingLogger.getInstance().getLogger().info(formatRecordTracking(\"POST REMOVE\"));";

        if (methodWithPostRemoveAnnot != null) {
//            code.append(debug);
            code.append(info);
            methodWithPostRemoveAnnot.insertBefore(code.toString());
		} else {
            code.append("public void onPostRemove() { ");
//            code.append(debug);
            code.append(info);
            code.append("}");

//            Logger.debug("PostRemove code%n%s", code.toString());

            final CtMethod onPostRemove = CtMethod.make(code.toString(), ctClass);
            ctClass.addMethod(onPostRemove);

            Annotation annotation = new Annotation(PostRemove.class.getName(), constPool);
            AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
            attr.addAnnotation(annotation);

            onPostRemove.getMethodInfo().addAttribute(attr);
        }
    }

    private void createMethodOnPreUpdate() throws Exception {
        // ----- Add onPreUpdate() method -----

        // Check if there's a method annotated with @PreUpdate
		CtMethod methodWithPreUpdateAnnot = EnhancerUtility.getMethodAnnotatedWith(ctClass, PreUpdate.class.getName());

        StringBuilder code = new StringBuilder();
//        String debug = "play.Logger.debug(\"SCOPE -> %s.onPreUpdate\", new String[]{this.getClass().getName()});";
        String info = "_fill_track_data();";

        if (methodWithPreUpdateAnnot != null) {
//            code.append(debug);
            code.append(info);
            methodWithPreUpdateAnnot.insertBefore(code.toString());
		} else {
            code.append("public void onPreUpdate() { ");
//            code.append(debug);
            code.append(info);
            code.append("}");

//            Logger.debug("PostUpdate code%n%s", code.toString());

            final CtMethod onPreUpdate = CtMethod.make(code.toString(), ctClass);
            ctClass.addMethod(onPreUpdate);

            Annotation annotation = new Annotation(PreUpdate.class.getName(), constPool);
            AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
            attr.addAnnotation(annotation);

            onPreUpdate.getMethodInfo().addAttribute(attr);
		}
    }

    private void createMethodOnPostUpdate() throws Exception {
        // ----- Add onPostUpdate() method -----

        // Check if there's a method annotated with @PreUpdate
		CtMethod methodWithPostUpdateAnnot = EnhancerUtility.getMethodAnnotatedWith(ctClass, PostUpdate.class.getName());

        StringBuilder code = new StringBuilder();
//        String debug = "play.Logger.debug(\"SCOPE -> %s.onPostUpdate\", new String[]{this.getClass().getName()});";
        String info = "play.modules.recordtracking.RecordTrackingLogger.getInstance().getLogger().info(formatRecordTracking(\"POST UPDATE\"));";

        if (methodWithPostUpdateAnnot != null) {
//            code.append(debug);
            code.append(info);
            methodWithPostUpdateAnnot.insertBefore(code.toString());
		} else {
            code.append("public void onPostUpdate() { ");
//            code.append(debug);
            code.append(info);
            code.append("}");

//            Logger.debug("PostUpdate code%n%s", code.toString());

            final CtMethod onPostUpdate = CtMethod.make(code.toString(), ctClass);
            ctClass.addMethod(onPostUpdate);

            Annotation annotation = new Annotation(PostUpdate.class.getName(), constPool);
            AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
            attr.addAnnotation(annotation);

            onPostUpdate.getMethodInfo().addAttribute(attr);
		}
    }

}
