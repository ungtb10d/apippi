apippi Custom SslContextFactory Example:
==========================================

Example-1: Custom SslContextFactory implementation based on Kubernetes secrets
------------------------------------------------------------------------------
For the documentation please refer to the javadocs for the K8SecretsSslContextFactory.java.


Installation:
============
Step 1: Build the apippi classes locally

change directory to <apippi_src_dir>
run "ant build"

Step 2: Run tests for the security examples

change directory to <apippi_src_dir>/examples/ssl-factory
run "ant test"
