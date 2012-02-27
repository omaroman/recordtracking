/**
 * Author: OMAROMAN
 * Date: 10/28/11
 * Time: 1:23 PM
 */
package play.modules.recordtracking;

import javassist.*;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import net.parnassoft.playutilities.EnhancerUtility;
import net.parnassoft.playutilities.annotations.Cast;
import net.parnassoft.playutilities.enums.Relationship;
import net.parnassoft.playutilities.exceptions.ForeignKeyException;
import net.parnassoft.playutilities.exceptions.TableNameException;
import play.Logger;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.classloading.enhancers.Enhancer;
import play.db.jpa.GenericModel;
import play.db.jpa.JPABase;
import play.db.jpa.Model;
import play.modules.recordtracking.annotations.Mask;
import play.modules.recordtracking.annotations.NoTracking;
import play.modules.recordtracking.exceptions.RecordTrackingException;
import play.modules.recordtracking.interfaces.Trackable;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
//        createMethodOnPreRemove();
        createMethodOnPostRemove();
        createMethodOnPreUpdate();
        createMethodOnPostUpdate();

        Logger.debug("RECORD_TRACKING ENHANCED: %s", ctClass.getName());

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

        // Skip enhance model classes if doesn't have a field annotated with @Id
        if (!EnhancerUtility.hasModelFieldAnnotatedWithIdWithinInheritance(ctClass)) {
            play.Logger.warn("WARNING: there is NO fields annotated with @Id");
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
        String code = "java.util.Map track_data;";
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
     * @throws RecordTrackingException -
     * @throws NoSuchFieldException -
     * @throws ForeignKeyException -
     * @throws TableNameException -
     */
    private void createFillTrackDataMethod() throws ClassNotFoundException, NotFoundException, RecordTrackingException, CannotCompileException, NoSuchFieldException, ForeignKeyException, TableNameException {
        //ctClass.getClassPool().importPackage("org.apache.commons.lang.StringUtils");

        StringBuilder code = new StringBuilder();
        code.append("public void _fill_track_data() {");
        code.append("track_data = new java.util.LinkedHashMap();"); // Here's created an instance of LinkedHashMap
        code.append("String key = null;");
        code.append("String value = null;");

        List<CtField> persistentFields = EnhancerUtility.getAllPersistentFieldsUpToGenericModel(ctClass);

        for (CtField ctField : persistentFields) {
            
            if (EnhancerUtility.isRelationship(ctField)) {

                // <--------
                code.append(fillTrackDataForRelationshipFields(ctField));
                // <--------

            } else {  // None-Relationship Field
                // <=====
                code.append(fillTrackDataForNoneRelationshipFields(ctField));
                // <=====
            }
        }
        

        // Print track_data, Uncomment the block just for debugging proposes
        /*code.append("java.util.Iterator entries = track_data.entrySet().iterator();");
        code.append("while (entries.hasNext()) {");
        code.append("java.util.Map.Entry thisEntry = (java.util.Map.Entry) entries.next();");
        code.append("String key = (String) thisEntry.getKey();");
        code.append("String value = (String) thisEntry.getValue();");
        code.append("play.Logger.debug(\"%s:%s\", new String[]{key, value});");
        code.append("}");*/

        code.append("}");   // end method

//        Logger.debug(code.toString());    // print the injected code

        final CtMethod fillTrackData = CtMethod.make(code.toString(), ctClass);
        ctClass.addMethod(fillTrackData);
    }
    
    private String fillTrackDataForRelationshipFields(CtField ctField) throws NotFoundException, ClassNotFoundException, RecordTrackingException, CannotCompileException, TableNameException, NoSuchFieldException, ForeignKeyException {
        // TODO: Implement logic
        StringBuilder code = new StringBuilder();

        Relationship relationship = EnhancerUtility.getRelationshipType(ctField);
        switch (relationship) {
            case ONE_TO_MANY_INVERSE:
                code.append(fillTrackDataForOneToManyInverseField(ctField));
                break;
            case ONE_TO_ONE_NORMAL:
                code.append(fillTrackDataForOneToOneNormalField(ctField));
        }
        
        /*if (EnhancerUtility.isList(ctField) || EnhancerUtility.isSet(ctField)) {
            // one-to-many (@OneToMany) or many-to-one (@ManyToOne) or many-to-many (@ManyToMany) association - A Collection (Set or List)
            // Get all "primary key fields" from unknown children by using a native Query in order to skip the loading of a collection of objects


        } else {    // one-to-one association (@OneToOne_Normal)

            // #####
            code.append(fillTrackDataForOneToOneNormalField(ctField));
            // #####
        }*/

        return code.toString();
    }
    
    private String fillTrackDataForOneToManyInverseField(CtField ctField) throws ClassNotFoundException, RecordTrackingException, CannotCompileException, TableNameException, NotFoundException, NoSuchFieldException, ForeignKeyException {
        // TODO: Implement logic
        StringBuilder code = new StringBuilder();

        // NOTE:
        // Java implements generics with type erasure. The compiler uses this type information only for
        // safety checks, the byte code doesn't contain any information about generic types.
        // Consequently, the runtime doesn't know about it either, so you cannot retrieve it by reflection.

        Cast cast = (Cast) EnhancerUtility.getAnnotation(ctField, Cast.class);
        if (cast == null || cast.value() == null) {
            String error = String.format("Unknown Cast Type for %s collection", ctField.getName());
            throw new RecordTrackingException(error);
        }

        CtClass type;
        try {
            type = classPool.get(cast.value().getName());
        } catch (NotFoundException e) {
            String error = String.format("%s not found in class pool", cast.value().getName());
            throw new CannotCompileException(error);
        }
        play.Logger.debug("MASTER: %s", type.getName());
        play.Logger.debug("SLAVE: %s", ctClass.getName());
        String modelClass;
        String idField;
        String table = EnhancerUtility.getTableName(type);

        Map.Entry<CtClass, CtField> modelWithId = EnhancerUtility.modelHavingFieldAnnotatedWithId(type);
        if (modelWithId != null) {
            //modelClass = modelWithId.getKey().getName();
            idField = modelWithId.getValue().getName();
        } else {
            throw new CannotCompileException("ALERT: modelWithId not found");
        }

        //if (EnhancerUtility.isInverseRelationship(ctField)) {  // && isOneToMany
        /*if (relationship == Relationship.ONE_TO_MANY_INVERSE) {*/
            // NOTE: This only works for INVERSE associations
            String slavePK = EnhancerUtility.getPrimaryKeyFieldName(type);
            String slaveFK = EnhancerUtility.getForeignKeyFieldName(type, ctClass); // master & slave
            Map.Entry<CtClass, CtField> modelField = EnhancerUtility.modelHavingFieldAnnotatedWithId(ctClass);
            CtClass model = modelField.getKey(); // the model
            CtField masterPK = modelField.getValue(); // the field

            code.append("Long fkValue = ((").append(model.getName()).append(")this).").append(masterPK.getName()).append(";");
            code.append("StringBuilder query = new StringBuilder();");
            code.append("query.append(\"SELECT \");");
            code.append("query.append(\"").append(idField).append("\");");
            code.append("query.append(\" FROM \");");
            code.append("query.append(\"").append(table).append("\");");
            code.append("query.append(\" WHERE \");");
            code.append("query.append(\"").append(slaveFK).append("\");");
            code.append("query.append(\" = \");");
            code.append("query.append(fkValue);");
            code.append("play.Logger.debug(\"QUERY: %s\", new String[]{query.toString()});");
            code.append("StringBuilder valueSB = new StringBuilder();");
            code.append("java.sql.ResultSet rs = play.db.DB.executeQuery(query.toString());");
            code.append("while (rs.next()) {");
            code.append("Object _id = rs.getObject(\"").append(idField).append("\");");
            code.append("valueSB.append(_id.toString()).append(' ');");
            code.append("}"); // end while block
            code.append("key = \"@").append(ctField.getName()).append("_").append(slavePK).append("'s\";");
            code.append("if (valueSB.length() == 0) {");
            code.append("valueSB.append(\"__NONE__\");");
            //code.append("} else {");
            //code.append("for (java.util.Iterator i = fkList.iterator(); i.hasNext(); ) {");
            //code.append("valueSB.append(i.next().toString()").append(").append(' ');");
            //code.append("}");   // end for block
            code.append("}");   // end if block
            code.append("track_data.put(key, valueSB.toString());");
        /*}*/

        /*code.append("key = \"@").append(ctField.getName()).append("_ids\";");
       code.append("StringBuilder valueSB = new StringBuilder();");
       code.append("if (").append(ctField.getName()).append(" != null) {");
       code.append("for (java.util.Iterator i =").append(ctField.getName()).append(".iterator(); i.hasNext(); ) {");
       code.append(modelClass).append(" model = (").append(modelClass).append(") i.next();"); // Cast to Model (or to What???)
       code.append("if (model == null || ((").append(modelClass).append(")model).").append(idField).append(" == null) {");
       code.append("valueSB.append(\"null\").append(' ');");
       code.append("} else {");
       code.append("valueSB.append(((").append(modelClass).append(")model).").append(idField).append(").append(' ');");
       code.append("}");   // end if
       code.append("}");   // end for
       code.append("} else {");
       code.append("valueSB.append(\"null\");");
       code.append("}");   // end if
       code.append("track_data.put(key, valueSB.toString());");*/

        return code.toString();
    }
    
    private String fillTrackDataForOneToOneNormalField(CtField ctField) throws NotFoundException, ClassNotFoundException, CannotCompileException {
        StringBuilder code = new StringBuilder();

        CtClass type = ctField.getType();
        String modelClass;
        String idField;
        //String table;

        Map.Entry<CtClass, CtField> modelWithId = EnhancerUtility.modelHavingFieldAnnotatedWithId(ctField.getType());
        if (modelWithId != null) {
            modelClass = modelWithId.getKey().getName();
            idField = modelWithId.getValue().getName();
            //table = getTableName(type); // The model annotated with @Entity
        } else {
            throw new CannotCompileException("WATCH OUT: modelWithId not found");
        }


        String slavePK = EnhancerUtility.getPrimaryKeyFieldName(type);
        /*// TODO: I guess this works only for NORMAL associations
       //String fk = EnhancerUtility.getForeignKeyFieldName(type, ctClass); // refClass & refField
       String fk = EnhancerUtility.getRelationshipKeyFieldName(Relationship.NORMAL, ctClass, type); // refClass & refField
       Map.Entry<CtClass, CtField> modelField = EnhancerUtility.modelHavingFieldAnnotatedWithId(ctClass);
       CtClass model = modelField.getKey();
       CtField pk = modelField.getValue();

       //code.append("Long fkValue = ((").append(model.getName()).append(")this).").append(pk.getName()).append(";");
       code.append("Long fkValue = ((").append(model.getName()).append(")this).").append(fk).append(";");
       code.append("StringBuilder query = new StringBuilder();");
       code.append("query.append(\"SELECT \");");
       code.append("query.append(\"").append(idField).append("\");");
       code.append("query.append(\" FROM \");");
       code.append("query.append(\"").append(table).append("\");");
       code.append("query.append(\" WHERE \");");
       //code.append("query.append(\"").append(fk).append("\");");
       code.append("query.append(\"").append(pk).append("\");");
       code.append("query.append(\" = \");");
       code.append("query.append(fkValue);");
       code.append("play.Logger.debug(\"QUERY: %s\", new String[]{query.toString()});");
       code.append("StringBuilder valueSB = new StringBuilder();");
       code.append("java.sql.ResultSet rs = play.db.DB.executeQuery(query.toString());");
       code.append("key = \"@").append(ctField.getName()).append("_").append(fk).append("\";");
       code.append("if (rs.next()) {");
       code.append("while (rs.next()) {");
       code.append("Object _id = rs.getObject(\"").append(idField).append("\");");
       code.append("value = _id.toString();");
       code.append("}"); // end while block
       code.append("} else {");
       code.append("value = \"null\";");
       code.append("}");   // end if block
       code.append("track_data.put(key, valueSB.toString());");*/

        // NOTE: No nativeQuery needed when is not a collection

        code.append("key = \"@").append(ctField.getName()).append("_").append(slavePK).append("\";");
        code.append(modelClass).append(" model = (").append(modelClass).append(")this.").append(ctField.getName()).append(";");
        code.append("if (model == null || ((").append(modelClass).append(")model).").append(idField).append(" == null) {");
        code.append("value = \"__NONE__\";");
        code.append("} else {");
        code.append("value = ((").append(modelClass).append(")model).").append(idField).append(".toString();");
        code.append("}");   // end if
        code.append("track_data.put(key, value);");

        return code.toString();
    }
    
    private String fillTrackDataForNoneRelationshipFields(CtField ctField) throws ClassNotFoundException, NotFoundException {
        StringBuilder code = new StringBuilder();
        
        if (EnhancerUtility.hasAnnotation(ctField, Mask.class.getName())) { // Mask Value
            if (ctField.getType().isPrimitive()) {
                // Create and instance wrapper of the primitive in order to invoke its toString method
                code.append("value = org.apache.commons.lang.StringUtils.repeat(\"*\", ").append(buildNewPrimitiveWrapper(ctField)).append(".toString().length());");
            } else {
                code.append("if (").append("((").append(ctClass.getName()).append(")this).").append(ctField.getName()).append(" == null) {");
                code.append("value = \"__NONE__\";");
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
                code.append("value = \"__NONE__\";");
                code.append("} else {");
                code.append("value = ((").append(ctClass.getName()).append(")this).").append(ctField.getName()).append(".toString();");
                code.append("}");   // end if
            }
        }
        code.append("key = \"@").append(ctField.getName()).append("\";");
        code.append("track_data.put(key, value);");
        
        return code.toString();
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
        Logger.debug("Creating formatRecordTracking method for %s", ctClass.getName());

        //List<CtClass> mappedSupperClasses = EnhancerUtility.mappedSuperClassesUpToJPABase(ctClass);
        /*mappedSupperClasses.add(0, ctClass);    // add at first place
        Collections.reverse(mappedSupperClasses);*/

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
        code.append("sb.append(\"\\n\");"); // new line

        code.append("sb.append(\"\\n\");"); // new line

        code.append("sb.append(\"<").append(ctClass.getName()).append(">\");");   // Model name
        code.append("sb.append(\"\\n\");"); // new line

        // TODO: If track_data is null then iterate over the real persistent fields

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
        String statement = ""; // Nothing to do

        if (methodWithPrePersistAnnot != null) {
            code.append("{");
            code.append(statement);
            code.append("}");
            methodWithPrePersistAnnot.insertBefore(code.toString());
		} else {
            code.append("public void onPostPersist() { ");
            code.append(statement);
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
        String statement = "_fill_track_data(); play.modules.recordtracking.RecordTrackingLogger.getInstance().getLogger().info(formatRecordTracking(\"POST PERSIST\"));";

        if (methodWithPostPersistAnnot != null) {
            code.append("{");
            code.append(statement);
            code.append("}");
            methodWithPostPersistAnnot.insertBefore(code.toString());
		} else {
            code.append("public void onPostPersist() { ");
            code.append(statement);
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
        String statement = "_fill_track_data();";

        if (methodWithPreRemoveAnnot != null) {
            code.append("{");
            code.append(statement);
            code.append("}");
            methodWithPreRemoveAnnot.insertBefore(code.toString());
		} else {
            code.append("public void onPreRemove() { ");
            code.append(statement);
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
        String statement = "_fill_track_data(); play.modules.recordtracking.RecordTrackingLogger.getInstance().getLogger().info(formatRecordTracking(\"POST REMOVE\"));";

        if (methodWithPostRemoveAnnot != null) {
            code.append("{");
            code.append(statement);
            code.append("}");
            methodWithPostRemoveAnnot.insertBefore(code.toString());
		} else {
            code.append("public void onPostRemove() { ");
            code.append(statement);
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

        /*
        EXPLICIT SAVE (taken from http://www.playframework.org/documentation/1.2.4/jpa
        Hibernate maintains a cache of Objects that have been queried from the database. These
        Objects are referred to as persistent Objects as long as the EntityManager that was used to
        fetch them is still active. That means that any changes to these Objects within the bounds of a
        transaction are automatically persisted when the transaction is committed
        */

        // 0 - Get the model id
        Map.Entry<CtClass, CtField> modelField = EnhancerUtility.modelHavingFieldAnnotatedWithId(ctClass);
        CtClass c = modelField.getKey();
        CtField f = modelField.getValue();
        code.append("Long id = ((").append(c.getName()).append(")this).").append(f.getName()).append(";");

        // 1 - Get an entity/model from the DB using another Persistence Context in order to have an unedited entity/model
        code.append(ctClass.getName()).append(" model = ").append("play.modules.recordtracking.RecordTracking.em.find(").append(ctClass.getName()).append(".class, id);");

        // 2 - model._fill_track_data();
        code.append("((").append(ctClass.getName()).append(")model)._fill_track_data();");

        // 3 - Write into log
        code.append("play.modules.recordtracking.RecordTrackingLogger.getInstance().getLogger().info(((").append(ctClass.getName()).append(")model).formatRecordTracking(\"PRE UPDATE\"));");


        if (methodWithPreUpdateAnnot != null) {
            String block = String.format("{%s}", code.toString());
            methodWithPreUpdateAnnot.insertBefore(block);
		} else {
            String tmpCode = code.toString();
            code = new StringBuilder();
            code.append("public void onPreUpdate() { ");
            code.append(tmpCode);
            code.append("}");

            final CtMethod onPreUpdate = CtMethod.make(code.toString(), ctClass);
            ctClass.addMethod(onPreUpdate);

            Annotation annotation = new Annotation(PreUpdate.class.getName(), constPool);
            AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
            attr.addAnnotation(annotation);

            onPreUpdate.getMethodInfo().addAttribute(attr);
		}

//        Logger.debug("PreUpdate code%n%s", code.toString());
    }

    private void createMethodOnPostUpdate() throws Exception {
        // ----- Add onPostUpdate() method -----

        // Check if there's a method annotated with @PreUpdate
		CtMethod methodWithPostUpdateAnnot = EnhancerUtility.getMethodAnnotatedWith(ctClass, PostUpdate.class.getName());

        StringBuilder code = new StringBuilder();
        String statement = "_fill_track_data(); play.modules.recordtracking.RecordTrackingLogger.getInstance().getLogger().info(formatRecordTracking(\"POST UPDATE\"));";

        if (methodWithPostUpdateAnnot != null) {
            code.append("{");
            code.append(statement);
            code.append("{");
            methodWithPostUpdateAnnot.insertBefore(code.toString());
		} else {
            code.append("public void onPostUpdate() { ");
            code.append(statement);
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