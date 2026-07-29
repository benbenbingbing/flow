package com.workflow.contracts.process.open;

/**
 * Publishes process lifecycle facts for externally bound process instances.
 */
public interface OpenProcessEventPort {

    void publish(OpenProcessEvent event);
}
