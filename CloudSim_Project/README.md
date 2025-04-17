# ☁️ CloudSim AWS Lambda Simulation

This project simulates **AWS Lambda-like serverless function behavior** using [CloudSim 3.0.3](http://www.cloudbus.org/cloudsim/), a framework for modeling and simulating cloud computing environments and evaluating resource provisioning algorithms in the cloud.

It is designed to replicate function-as-a-service (FaaS) behavior, where each function is invoked, executed, and terminated, mimicking stateless function execution in a cloud datacenter environment.

---

## 📁 Project Structure

```
.
├── .github/
│   └── workflows/
│       └── main.yml              # GitHub Actions workflow
├── CloudSim_Project/
│   ├── libs/                     # External JAR dependencies (CloudSim, Math, etc.)
│   │   ├── cloudsim-3.0.3.jar
│   ├── src/
│   │   └── main/
│   │       └── java/
│   │           └── org/example/AWS_Lambda_Simulation.java
│   ├── pom.xml                   # Maven build file
├── .gitignore
└── README.md
```

---

## 🚀 How to Run Locally

To run the simulation locally, follow these steps:

### 📌 Step 1: Navigate to the Project Directory

Open a terminal and navigate into the `CloudSim_Project` folder:

```bash
cd CloudSim_Project
```

### 📦 Step 2: Install the CloudSim JAR into Local Maven Repository

CloudSim is not available in the Maven Central Repository, so we need to manually install it into the local Maven repository:

```bash
mvn install:install-file \
  -Dfile=libs/cloudsim-3.0.3.jar \
  -DgroupId=org.cloudbus \
  -DartifactId=cloudsim \
  -Dversion=3.0.3 \
  -Dpackaging=jar
```

📍 **Note:** This command must be run from **inside the `CloudSim_Project` folder** where the `libs/` folder and `pom.xml` are located.

---

### 🧪 Step 3: Compile and Run the Simulation

Once the JAR is installed and your Maven setup is ready, compile and run the simulation using:

```bash
mvn clean compile exec:java
```

This will:
- Compile the Java simulation class (`AWS_Lambda_Simulation`)
- Execute the main class as defined in `pom.xml`

Make sure `pom.xml` has this configuration:

```xml
<mainClass>org.example.AWS_Lambda_Simulation</mainClass>
```

---

## 🤖 GitHub Actions CI

This project includes a GitHub Actions workflow to:
1. Set up Java
2. Compile the simulation file with dependencies
3. Run the simulation

You can find it at: `.github/workflows/main.yml`.

No additional setup is needed if your repo is connected to GitHub.

---

## 🧰 Prerequisites

- Java 22 (Temurin recommended)
- Maven 3+
- CloudSim 3.0.3 JAR and related dependencies in the `libs/` folder

---

## 🧠 Why This Project?

This simulation helps understand:
- Stateless function invocation and termination
- Resource utilization for short-lived tasks
- Real-time scheduling policies for serverless compute

Perfect for coursework, research, or prototyping resource allocation strategies for cloud environments.

---

## 📄 License

This project is open-source under the [MIT License](LICENSE).

## Author 

Abhishek Rajput

---