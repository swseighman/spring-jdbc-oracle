Clone repo.

#### Starting the Oracle Database

For simplicity, we'll use a container to deploy the Oracle Database. In this exaample, we'll use Oracle Database XE 18c (Express Edition).

```
docker run -d -p 1521:1521 -p 5500:5500 -e ORACLE_PASSWORD=password -v oracle-volume:/opt/oracle/oradata gvenzl/oracle-xe
```

Install Required Packages
```
sudo dnf install libaio
sudo rpm -ihv oracle-instantclient-basic-21.5.0.0.0-1.el8.x86_64.rpm
sudo rpm -ihv oracle-instantclient-sqlplus-21.5.0.0.0-1.el8.x86_64.rpm
```

**Test/Access the Database**
```
sqlplus system/password@localhost:1521/XE
```

#### Build the Project

```
mvn package -Pnative
target/spring-jdbc-oracle
```

You should see the output below as the query returns REGIONS in the table:
```
... <snip>
2022-04-08 11:56:56.240  INFO 1696 --- [           main] com.example.oradbsample.App              : {REGION_ID=1, REGION_NAME=Europe}
2022-04-08 11:56:56.240  INFO 1696 --- [           main] com.example.oradbsample.App              : {REGION_ID=2, REGION_NAME=Americas}
2022-04-08 11:56:56.241  INFO 1696 --- [           main] com.example.oradbsample.App              : {REGION_ID=3, REGION_NAME=Asia}
2022-04-08 11:56:56.241  INFO 1696 --- [           main] com.example.oradbsample.App              : {REGION_ID=4, REGION_NAME=Middle East and Africa}
... <snip>
```


#### Kubernetes Configuration and Setup (minikube)

First, we'll need to install `minikube`, follow the instructions here: 
https://minikube.sigs.k8s.io/docs/start/

Next, install `kubectl`, see the instructions here:
https://kubernetes.io/docs/tasks/tools/install-kubectl/

Finally, install `kn`, see instructions here: https://knative.dev/docs/install/client/install-kn/#install-the-kn-cli


```
minikube update-check
```

```
minikube delete
```


```
minikube config set memory 8192
minikube config set cpus 4
minikube config set driver docker
minikube config set kubernetes-version v1.23.5
```


```
minikube config view
```

```
minikube start
```

```
minikube tunnel
```


```
minikube status
```

```
minikube dashboard
```


#### Build Containers
```
eval $(minikube docker-env)
```

```
docker build -f ./Dockerfile.jvm -t localhost/spring-jdbc-oracle:jvm .
```

```
docker build -f ./Dockerfile.native -t localhost/spring-jdbc-oracle:native .
```

```
minikube ssh
```

```
docker images | grep oracle
```

#### Deploy Oracle Database XE
```
kubectl create namespace oracle
```

```
kubectl get namespace oracle
```

```
kubectl create configmap oradb --from-env-file=oracle.properties -n oracle
```

```
kubectl apply -f oradb18xe.yaml -n oracle
```

```
kubectl get deployments -n oracle
```

```
kubectl get pods -n oracle
```

```
kubectl get services -n oracle
```

```
minikube service oracle18xe -n oracle --url
```

```
kubectl port-forward -n oracle oracle18xe-578fb89fc5-rmrcr 1521:1521
```

```
sqlplus system/password@localhost:45485/XEPDB1
```

#### Install Knative Serving

1. Select the version of Knative Serving to install
    ```bash
    export KNATIVE_VERSION="1.3.0"
    ```
1. Install Knative Serving in namespace `knative-serving`
    ```bash
    kubectl apply -f https://github.com/knative/serving/releases/download/knative-v${KNATIVE_VERSION}/serving-crds.yaml
    kubectl wait --for=condition=Established --all crd

    kubectl apply -f https://github.com/knative/serving/releases/download/knative-v${KNATIVE_VERSION}/serving-core.yaml

    kubectl wait pod --timeout=-1s --for=condition=Ready -l '!job-name' -n knative-serving > /dev/null
    ```
1. Select the version of Knative Net Kourier to install
    ```bash
    export KNATIVE_NET_KOURIER_VERSION="1.3.0"
    ```

1. Install Knative Layer kourier in namespace `kourier-system`
    ```bash
    kubectl apply -f https://github.com/knative/net-kourier/releases/download/knative-v${KNATIVE_NET_KOURIER_VERSION}/kourier.yaml
    kubectl wait pod --timeout=-1s --for=condition=Ready -l '!job-name' -n kourier-system
    kubectl wait pod --timeout=-1s --for=condition=Ready -l '!job-name' -n knative-serving
    ```
1. Set the environment variable `EXTERNAL_IP` to External IP Address of the Worker Node, you might need to run this command multiple times until service is ready.
    ```bash
    EXTERNAL_IP=$(kubectl -n kourier-system get service kourier -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
    echo EXTERNAL_IP=$EXTERNAL_IP
    ```
2. Set the environment variable `KNATIVE_DOMAIN` as the DNS domain using `sslip.io`
    ```bash
    KNATIVE_DOMAIN="$EXTERNAL_IP.sslip.io"
    echo KNATIVE_DOMAIN=$KNATIVE_DOMAIN
    ```
    Double-check DNS is resolving
    ```bash
    dig $KNATIVE_DOMAIN
    ```
1. Configure DNS for Knative Serving
    ```bash
    kubectl patch configmap -n knative-serving config-domain -p "{\"data\": {\"$KNATIVE_DOMAIN\": \"\"}}"
    ```
1. Configure Knative to use Kourier
    ```bash
    kubectl patch configmap/config-network \
      --namespace knative-serving \
      --type merge \
      --patch '{"data":{"ingress.class":"kourier.ingress.networking.knative.dev"}}'
    ```
1. Verify that Knative is Installed properly all pods should be in `Running` state and our `kourier-ingress` service configured.
    ```bash
    kubectl get pods -n knative-serving
    kubectl get pods -n kourier-system
    kubectl get svc  -n kourier-system
    ```


#### Deploy Knative Serving Application

Deploy using [kn](https://github.com/knative/client)
```bash
kn service create hello \
--image gcr.io/knative-samples/helloworld-go \
--port 8080 \
--env TARGET=Knative
```

**Optional:** Deploy a Knative Service using the equivalent yaml manifest:
```bash
cat <<EOF | kubectl apply -f -
apiVersion: serving.knative.dev/v1
kind: Service
metadata:
  name: hello
spec:
  template:
    spec:
      containers:
        - image: gcr.io/knative-samples/helloworld-go
          ports:
            - containerPort: 8080
          env:
            - name: TARGET
              value: "Knative"
EOF
```

Wait for Knative Service to be Ready
```bash
kubectl wait ksvc hello --all --timeout=-1s --for=condition=Ready
```

Get the URL of the new Service
```bash
SERVICE_URL=$(kubectl get ksvc hello -o jsonpath='{.status.url}')
echo $SERVICE_URL
```

Test the App
```bash
curl $SERVICE_URL
```

The output should be:
```
Hello Knative!
```

Check the knative pods that scaled from zero
```
kubectl get pod -l serving.knative.dev/service=hello
```

The output should be:
```
NAME                                     READY   STATUS    RESTARTS   AGE
hello-r4vz7-deployment-c5d4b88f7-ks95l   2/2     Running   0          7s
```

Try the service `url` on your browser (command works on linux and macos)
```bash
open $SERVICE_URL
```

You can watch the pods and see how they scale down to zero after http traffic stops to the url
```
kubectl get pod -l serving.knative.dev/service=hello -w
```

The output should look like this:
```
NAME                                     READY   STATUS
hello-r4vz7-deployment-c5d4b88f7-ks95l   2/2     Running
hello-r4vz7-deployment-c5d4b88f7-ks95l   2/2     Terminating
hello-r4vz7-deployment-c5d4b88f7-ks95l   1/2     Terminating
hello-r4vz7-deployment-c5d4b88f7-ks95l   0/2     Terminating
```

Try to access the url again, and you will see a new pod running again.
```
NAME                                     READY   STATUS
hello-r4vz7-deployment-c5d4b88f7-rr8cd   0/2     Pending
hello-r4vz7-deployment-c5d4b88f7-rr8cd   0/2     ContainerCreating
hello-r4vz7-deployment-c5d4b88f7-rr8cd   1/2     Running
hello-r4vz7-deployment-c5d4b88f7-rr8cd   2/2     Running
```

>FYI, there is also a `kn` plugin (still in Beta) which will perform an automated install of the Knative environment.  See more info [here](https://github.com/knative-sandbox/kn-plugin-quickstart).
After installing the plugin, you would execute:
> ```
>kn quickstart minikube
>```

```
minikube stop
```











