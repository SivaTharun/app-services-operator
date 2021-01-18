package com.openshift.cloud.beans;

import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.openshift.cloud.v1alpha.models.ManagedKafkaConnection;
import com.openshift.cloud.v1alpha.models.ManagedKafkaConnectionList;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.V1beta1ApiextensionAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;

/**
 * This class is processed at startup and checks that CRDs are installed and
 * provides apiClients for use
 */
@Singleton
public final class ApiClients {

    private static final Logger LOG = Logger.getLogger(ApiClients.class.getName());

    @Inject
    KubernetesClient client;

    private CustomResourceDefinition mkcCrd;

    @PostConstruct
    public void init() {
        LOG.info("ApiClient bean init begun");

        var crds = client.apiextensions().v1beta1();

        this.mkcCrd = initManagedKafkaConnectionCRDAndClient(crds);

        LOG.info("ApiClient bean init ended");

    }

    public MixedOperation<ManagedKafkaConnection, ManagedKafkaConnectionList, Resource<ManagedKafkaConnection>> managedKafkaConnection() {
        KubernetesDeserializer.registerCustomKind(getApiVersion(ManagedKafkaConnection.class), mkcCrd.getKind(),
                ManagedKafkaConnection.class);

        var mkcCrdContext = CustomResourceDefinitionContext.fromCrd(this.mkcCrd);

        // lets create a client for the CRD
        return client.customResources(mkcCrdContext, ManagedKafkaConnection.class, ManagedKafkaConnectionList.class);
    }

    private CustomResourceDefinition initManagedKafkaConnectionCRDAndClient(
            V1beta1ApiextensionAPIGroupDSL crds) {

        CustomResourceDefinition mkcCrd;

        var crdsItems = crds.customResourceDefinitions().list().getItems();
        var managedKafkaConnectionCRDName = CustomResource.getCRDName(ManagedKafkaConnection.class);
        
        var mkcCrdOptional = crdsItems.stream()
               .filter(
                   crd -> managedKafkaConnectionCRDName.equals(crd.getMetadata().getName())
                )
               .findFirst();

        if (mkcCrdOptional.isEmpty()) {
            LOG.info("Creating ManagedKafkaConnection CRD");
            mkcCrd = CustomResourceDefinitionContext.v1beta1CRDFromCustomResourceType(ManagedKafkaConnection.class)
                    .build();
            client.apiextensions().v1beta1().customResourceDefinitions().create(mkcCrd);
            LOG.info("ManagedKafkaConnection CRD Created");
        } else {
            LOG.info("Found ManagedKafkaConnection CRD");
            mkcCrd = mkcCrdOptional.get();
        }

        return mkcCrd;

    }

    /**
     * Computes the {@code apiVersion} associated with this HasMetadata
     * implementation. The value is derived from the {@link Group} and
     * {@link Version} annotations.
     *
     * @param clazz the HasMetadata whose {@code apiVersion} we want to compute
     * @return the computed {@code apiVersion} or {@code null} if neither
     *         {@link Group} or {@link Version} annotations are present
     * @throws IllegalArgumentException if only one of {@link Group} or
     *                                  {@link Version} is provided
     */
    static String getApiVersion(Class<? extends HasMetadata> clazz) {
        final String group = getGroup(clazz);
        final String version = getVersion(clazz);
        if (group != null && version != null) {
            return group + "/" + version;
        }
        if (group != null || version != null) {
            throw new IllegalArgumentException("You need to specify both @" + Group.class.getSimpleName() + " and @"
                    + Version.class.getSimpleName() + " annotations if you specify either");
        }
        return null;
    }

    /**
     * Retrieves the group associated with the specified HasMetadata as defined by
     * the {@link Group} annotation.
     *
     * @param clazz the HasMetadata whose group we want to retrieve
     * @return the associated group or {@code null} if the HasMetadata is not
     *         annotated with {@link Group}
     */
    static String getGroup(Class<? extends HasMetadata> clazz) {
        final Group group = clazz.getAnnotation(Group.class);
        return group != null ? group.value() : null;
    }

    /**
     * Retrieves the version associated with the specified HasMetadata as defined by
     * the {@link Version} annotation.
     *
     * @param clazz the HasMetadata whose version we want to retrieve
     * @return the associated version or {@code null} if the HasMetadata is not
     *         annotated with {@link Version}
     */
    static String getVersion(Class<? extends HasMetadata> clazz) {
        final Version version = clazz.getAnnotation(Version.class);
        return version != null ? version.value() : null;
    }


}