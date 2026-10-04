package nl.thorhaven.app;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Instrumentation-only privileged fixture controls; document access is a separate SAF provider. */
public class StorageFixtureControlProvider extends ContentProvider {
  @Override
  public boolean onCreate() {
    return true;
  }

  @Override
  public Bundle call(String method, String arg, Bundle extras) {
    getContext()
        .enforceCallingOrSelfPermission("android.permission.DUMP", "Storage QA fixture controls");
    // Both providers are in this test APK's process. Acquire as the provider-owning application,
    // not as the target app; this initializes it without opening any target document access.
    try (ContentProviderClient client =
        getContext()
            .getContentResolver()
            .acquireContentProviderClient(StorageTestProvider.AUTHORITY)) {
      StorageTestProvider documents = StorageTestProvider.instance;
      if (client == null || documents == null)
        throw new IllegalStateException("Storage QA documents provider unavailable");
      return documents.fixture(method, arg, extras);
    }
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getType(Uri uri) {
    return null;
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri uri, String selection, String[] args) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] args) {
    throw new UnsupportedOperationException();
  }
}
