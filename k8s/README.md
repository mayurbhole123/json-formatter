# Deploying json-formatter to minikube

## 1. Start the cluster

```bash
minikube start --driver=docker --cpus=4 --memory=6g
minikube addons enable ingress          # for k8s/ingress.yaml
minikube addons enable metrics-server   # for k8s/hpa.yaml
```

## 2. Build the image inside minikube's Docker daemon

Building against minikube's own daemon means no registry and no `docker push`.
The `eval` only affects the shell you run it in.

```bash
eval $(minikube docker-env)
docker build -t json-formatter:1.0.0 .
docker images | grep json-formatter
```

`imagePullPolicy: IfNotPresent` in the Deployment is what keeps the kubelet from
trying to pull `json-formatter:1.0.0` from Docker Hub.

## 3. Apply the manifests

```bash
kubectl apply -k k8s/
kubectl -n json-formatter rollout status deployment/json-formatter
kubectl -n json-formatter get pods,svc,ingress
```

## 4. Reach the app

Either through the NodePort service:

```bash
minikube service json-formatter-np -n json-formatter
```

or through the ingress (add the host to `/etc/hosts` first):

```bash
echo "$(minikube ip) json-formatter.local" | sudo tee -a /etc/hosts
open http://json-formatter.local
```

On the Docker driver on macOS the node IP is not routable from the host, so the
ingress route needs a tunnel running in a separate terminal:

```bash
minikube tunnel   # then use 127.0.0.1 in /etc/hosts instead of $(minikube ip)
```

## 5. Redeploy after a code change

```bash
eval $(minikube docker-env)
docker build -t json-formatter:1.0.0 .
kubectl -n json-formatter rollout restart deployment/json-formatter
```

Bump the tag (in `k8s/kustomization.yaml` under `images:`) instead of restarting
if you want the rollout tied to a real image version.

## Troubleshooting

```bash
kubectl -n json-formatter logs -l app.kubernetes.io/name=json-formatter --tail=100 -f
kubectl -n json-formatter describe pod -l app.kubernetes.io/name=json-formatter
kubectl -n json-formatter port-forward svc/json-formatter 8080:80
curl localhost:8080/actuator/health
```

`ErrImagePull` / `ImagePullBackOff` almost always means the image was built in
the host's Docker daemon rather than minikube's — re-run `eval $(minikube
docker-env)` and rebuild.
