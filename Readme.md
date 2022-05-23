# Deploy a Knative Service to Access an Oracle Database

The following lab is an example of building a SpringBoot application that is deployed as a Knative service running on `minikube`.  The service will query an Oracle Database (also running on `minikube`) and return some values.

There are two versions of the service, one is a standard JAR-based application while the other will utilize the GraalVM AOT (Ahead of Time) compiler feature to create a native image executable of the same service.  

The goal is to compare startup time for each service.

You'll need to have GraalVM (22.0.0.2 preferred) and the native image module installed.  You'll also need `docker` or `podman` installed.

**JDK 17** is highly recommended.


### Getting Started

First, clone this repository:
```
git clone https://github.com/swseighman/spring-jdbc-oracle.git
cd spring-jdbc-oracle
```

### Oracle Database Access Prerequisites
Install the required packages for installing/using SQLPLUS:
```
sudo dnf install libaio
```

Download and install the Instant Client and SQLPLUS from [here](https://www.oracle.com/database/technologies/instant-client.html).
```
sudo rpm -ihv oracle-instantclient-basic-21.5.0.0.0-1.el8.x86_64.rpm
sudo rpm -ihv oracle-instantclient-sqlplus-21.5.0.0.0-1.el8.x86_64.rpm
```

### Build the Project

```
mvn package -Pnative
```


### Kubernetes Configuration and Setup (minikube)

First, we'll need to install `minikube`, follow the instructions here: 
https://minikube.sigs.k8s.io/docs/start/

Next, install `kubectl`, see the instructions here:
https://kubernetes.io/docs/tasks/tools/install-kubectl/

Finally, install `kn`, see instructions here: https://knative.dev/docs/install/client/install-kn/#install-the-kn-cli

You can check to determine if your `minikube` is the latest version or whether an upgrade might be in order:
```
minikube update-check
```

If you have any old configurations in place, you can delete the existing configurations and begin new:
```
minikube delete
```

You may want to consider setting some configuration values for the `minikube` environment:
```
minikube config set memory 8192
minikube config set cpus 4
minikube config set driver docker
minikube config set kubernetes-version v1.23.5
```

You can view the current `minikube` configuration using:
```
minikube config view
```

To start `minikube`:
```
minikube start
```

>Services of type **LoadBalancer** can be exposed via the `minikube tunnel` command. It must be run in a separate terminal window to keep the LoadBalancer running. `Ctrl-C` in the terminal can be used to terminate the process at which time the network routes will be cleaned up.
>```
>minikube tunnel
>```

>If the `minikube tunnel` shuts down in an abrupt manner, it may leave orphaned network routes on your system. If this happens, the `~/.minikube/tunnels.json` file will contain an entry for that tunnel. To remove orphaned routes, run:
>```
>minikube tunnel --cleanup
>```


Check the status of your `minikube` cluster:
```
minikube status
```

The dashboard can be a convenient tool for troubleshooting any issues that may arise in your cluster.  To start the dashboard, execute:
```
minikube dashboard
```


### Build Containers

By using the following command, any `docker` command you run in this current terminal will run against the docker inside the `minikube` cluster:

```
eval $(minikube docker-env)
```

Build the containers:
```
docker build -f ./Dockerfile.jvm -t localhost/spring-jdbc-oracle:jvm .
```

```
docker build -f ./Dockerfile.native -t localhost/spring-jdbc-oracle:native .
```

The next two commands will show you the containers inside `minikube`, inside minikube’s VM or Container:
```
minikube ssh
```
```
docker images | grep oracle
```

### Deploy Oracle Database XE

Build the Oracle Database container:
```
git clone https://github.com/oracle/docker-images.git
cd docker-images/OracleDatabase/SingleInstance/dockerfiles
eval $(minikube docker-env)
./buildContainerImage.sh -v 18.4.0 -x
```

Create a namespace for the Oracle Database:
```
kubectl create namespace oracle
```

```
kubectl get namespace oracle
```

Create a ConfigMap:
```
kubectl create configmap oradb --from-env-file=oracle.properties -n oracle
```

Deploy the Oracle Database:
```
kubectl apply -f oradb18xe.yml -n oracle
```

Show the deployment:
```
kubectl get deployments -n oracle
```

Show the running pods:
```
kubectl get pods -n oracle
```

Show the running service:
```
kubectl get service oracle18xe -n oracle
```

Return a URL to connect to the Oracle Database service:
```
minikube service oracle18xe -n oracle --url
```

Show the running service:
```
kubectl get services -n oracle
```

Forward the local port to the Oracle Database:
```
kubectl port-forward -n oracle oracle18xe-7676b54784-5xj26 1521:1521
```

Connect to the Oracle Database:
```
sqlplus system/password@localhost:1521/XEPDB1
```

Run the SQL script to populate the sample database:
```
@load_sample.sql
```



### Install Knative Serving

1. Select the version of Knative Serving to install
    ```
    export KNATIVE_VERSION="1.3.0"
    ```
1. Install Knative Serving in namespace `knative-serving`
    ```
    kubectl apply -f https://github.com/knative/serving/releases/download/knative-v${KNATIVE_VERSION}/serving-crds.yaml
    kubectl wait --for=condition=Established --all crd

    kubectl apply -f https://github.com/knative/serving/releases/download/knative-v${KNATIVE_VERSION}/serving-core.yaml

    kubectl wait pod --timeout=-1s --for=condition=Ready -l '!job-name' -n knative-serving > /dev/null
    ```
1. Select the version of Knative Net Kourier to install
    ```
    export KNATIVE_NET_KOURIER_VERSION="1.3.0"
    ```

1. Install Knative Layer kourier in namespace `kourier-system`
    ```
    kubectl apply -f https://github.com/knative/net-kourier/releases/download/knative-v${KNATIVE_NET_KOURIER_VERSION}/kourier.yaml
    kubectl wait pod --timeout=-1s --for=condition=Ready -l '!job-name' -n kourier-system
    kubectl wait pod --timeout=-1s --for=condition=Ready -l '!job-name' -n knative-serving
    ```
1. Set the environment variable `EXTERNAL_IP` to External IP Address of the Worker Node, you might need to run this command multiple times until service is ready.
    ```
    EXTERNAL_IP=$(kubectl -n kourier-system get service kourier -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
    echo EXTERNAL_IP=$EXTERNAL_IP
    ```
2. Set the environment variable `KNATIVE_DOMAIN` as the DNS domain using `sslip.io`
    ```
    KNATIVE_DOMAIN="$EXTERNAL_IP.sslip.io"
    echo KNATIVE_DOMAIN=$KNATIVE_DOMAIN
    ```
    Double-check DNS is resolving
    ```
    dig $KNATIVE_DOMAIN
    ```
1. Configure DNS for Knative Serving
    ```
    kubectl patch configmap -n knative-serving config-domain -p "{\"data\": {\"$KNATIVE_DOMAIN\": \"\"}}"
    ```
1. Configure Knative to use Kourier
    ```
    kubectl patch configmap/config-network \
      --namespace knative-serving \
      --type merge \
      --patch '{"data":{"ingress.class":"kourier.ingress.networking.knative.dev"}}'
    ```
1. Verify that Knative is Installed properly all pods should be in `Running` state and our `kourier-ingress` service configured.
    ```
    kubectl get pods -n knative-serving
    kubectl get pods -n kourier-system
    kubectl get svc  -n kourier-system
    ```


### Deploy Knative Serving Application

Deploy a **JAR-based** Knative Service using the yaml manifest:

```
kubectl apply -f spring-oradb-jvm.yml
```

Wait for Knative Service to be Ready:
```
kubectl wait ksvc spring-oradb-jvm --all --timeout=-1s --for=condition=Ready
```

Get the URL of the new Service:
```
SERVICE_URL=$(kubectl get ksvc spring-oradb-jvm -o jsonpath='{.status.url}')
echo $SERVICE_URL
```

Check the knative pods that scaled from zero:
```
kubectl get pod -l serving.knative.dev/service=spring-oradb-jvm
```

The output should be:
```
NAME                                               READY    STATUS    RESTARTS   AGE
spring-oradb-jvm-r4vz7-deployment-c5d4b88f7-ks95l   1/1     Running   0          7s
```

You can watch the pods and see how they scale down to zero after the application completes it's query.
```
kubectl get pod -l serving.knative.dev/service=spring-oradb-jvm -w
```

The output should look like this:
```
NAME                                               READY    STATUS
spring-oradb-jvm-r4vz7-deployment-c5d4b88f7-ks95l   1/1     Running
spring-oradb-jvm-r4vz7-deployment-c5d4b88f7-ks95l   0/1     Terminating
```

You can delete the service by executing:
```
kn service delete spring-oradb-jvm
```

You can follow the same process for the native image version of the service, but use `spring-oradb-native`.

Deploy a **native image-based** Knative Service using the yaml manifest:

```
kubectl apply -f spring-oradb-native.yml
```

Wait for Knative Service to be Ready:
```
kubectl wait ksvc spring-oradb-native --all --timeout=-1s --for=condition=Ready
```

Get the URL of the new Service:
```
SERVICE_URL=$(kubectl get ksvc spring-oradb-native -o jsonpath='{.status.url}')
echo $SERVICE_URL
```

Check the knative pods that scaled from zero:
```
kubectl get pod -l serving.knative.dev/service=spring-oradb-native
```

The output should be:
```
NAME                                                  READY    STATUS    RESTARTS   AGE
spring-oradb-native-r4vz7-deployment-c5d4b88f7-ks95l   1/1     Running   0          7s
```

You can watch the pods and see how they scale down to zero after the application completes it's query:
```
kubectl get pod -l serving.knative.dev/service=spring-oradb-native -w
```

The output should look like this:
```
NAME                                                   READY   STATUS
spring-oradb-native-r4vz7-deployment-c5d4b88f7-ks95l   1/1     Running
spring-oradb-native-r4vz7-deployment-c5d4b88f7-ks95l   0/1     Terminating
```

You can delete the service by executing:
```
kn service delete spring-oradb-native
```

>FYI, there is also a `kn` plugin (still in Beta) which will perform an automated install of the Knative environment.  See more info [here](https://github.com/knative-sandbox/kn-plugin-quickstart).
After installing the plugin, you would execute:
> ```
>kn quickstart minikube
>```


To shutdow the `minikube` cluster, execute:
```
minikube stop
```











