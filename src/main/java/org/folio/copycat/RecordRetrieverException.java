package org.folio.copycat;

/**
 * Custom exception for errors in RecordRetriever.
 */
public class RecordRetrieverException extends RuntimeException {
  public RecordRetrieverException(String message) {
    super(message);
  }

  public RecordRetrieverException(String message, Throwable cause) {
    super(message, cause);
  }
}
