package com.rustyrazorblade.easycasslab.configuration

import  com.rustyrazorblade.easycasslab.Context
import  com.rustyrazorblade.easycasslab.EC2
import  com.rustyrazorblade.easycasslab.Utils
import org.apache.logging.log4j.kotlin.logger
import software.amazon.awssdk.regions.Region
import java.io.File
import software.amazon.awssdk.services.ec2.model.CreateKeyPairRequest
import java.nio.file.Files
import java.util.*
import java.nio.file.attribute.PosixFilePermission
import java.util.HashSet


data class User(
    var email : String,
    var region: String,
    var keyName: String,
    var sshKeyPath: String,

    // if true we'll load the profile from the AWS credentials rather than this file
    // can over
    var awsProfile: String,
    // fallback for people who haven't set up the aws cli

    var awsAccessKey: String,
    var awsSecret: String,

    var axonOpsOrg: String = "",
    var axonOpsKey: String = ""
) {
    companion object {

        val log = logger()

        /**
         * Asks a bunch of questions and generates the user file
         */
        fun createInteractively(context: Context, location: File) {
            println("Welcome to the easy-cass-lab interactive setup.")
            println("We just need to know a few things before we get started.")

            val email = Utils.prompt("What's your email?", "")

            // we're not honoring it, so we'll take this out
            val regionAnswer = Utils.prompt("What AWS region do you use?", "us-west-2")
            val region = Region.of(regionAnswer)

            val awsCredentialsFilePath = Utils.prompt("Please enter the path to your AWS credentials file (or leave blank to enter credentials directly):", "")
            val awsAccessKey = Utils.prompt("Please enter your AWS Access Key (leave blank if you provided a credentials file):", "")
            val awsSecret = Utils.prompt("Please enter your AWS Secret Access Key (leave blank if you provided a credentials file):", "", secret = true)

            // create the key pair

            println("Attempting to validate credentials and generate easy-cass-lab login keys")
            val ec2 = EC2(awsCredentialsFilePath, awsAccessKey, awsSecret, region)
            val ec2Client = ec2.client

            val keyName = "easy-cass-lab-${UUID.randomUUID()}"
            val request = CreateKeyPairRequest.builder()
                    .keyName(keyName).build()

            val response = ec2Client.createKeyPair(request)

            // write the private key into the ~/.easy-cass-lab/profiles/<profile>/ dir

            val secret = File(context.profileDir, "secret.pem")
            secret.writeText(response.keyMaterial())

            fun getAxonOps(inputName : String) =
                Utils.prompt("AxonOps $inputName: ", "")

            val axonOpsChoice = Utils.prompt("Use AxonOps (https://axonops.com/) for monitoring. Requires an account. [y/N]", default = "N")
            val useAxonOps = axonOpsChoice.equals("y", true);
            val axonOpsOrg =  if (useAxonOps) getAxonOps("Org") else ""
            val axonOpsKey =  if (useAxonOps) getAxonOps("Key") else ""

            // set permissions
            val perms = HashSet<PosixFilePermission>()
            perms.add(PosixFilePermission.OWNER_READ)
            perms.add(PosixFilePermission.OWNER_WRITE)

            log.info { "Setting secret file permissions $perms"}
            Files.setPosixFilePermissions(secret.toPath(), perms)


            val user = User(
                email,
                region.toString(),
                keyName,
                secret.absolutePath,
                "", // future compatibility, when we start allowing people to use their existing AWS creds they've already set up.
                awsAccessKey,
                awsSecret,
                axonOpsOrg,
                axonOpsKey)

            context.yaml.writeValue(location, user)
        }
    }
}



