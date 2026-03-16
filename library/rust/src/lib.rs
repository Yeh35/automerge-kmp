use automerge::{AutoCommit, ReadDoc, transaction::Transactable, ObjType, ObjId, ROOT};
use std::sync::Mutex;
use std::collections::HashMap;

uniffi::setup_scaffolding!();

/// Internal storage for mapping string handles to ObjIds.
struct DocInner {
    doc: AutoCommit,
    obj_map: HashMap<String, ObjId>,
    next_id: u64,
}

impl DocInner {
    fn new(doc: AutoCommit) -> Self {
        Self {
            doc,
            obj_map: HashMap::new(),
            next_id: 1,
        }
    }

    fn register_obj(&mut self, obj_id: ObjId) -> String {
        let handle = format!("obj_{}", self.next_id);
        self.next_id += 1;
        self.obj_map.insert(handle.clone(), obj_id);
        handle
    }

    fn resolve_obj(&self, handle: &str) -> Result<&ObjId, AutomergeError> {
        self.obj_map.get(handle).ok_or(AutomergeError::InvalidObjectId {
            msg: format!("Unknown object handle: {}", handle),
        })
    }
}

/// A CRDT document backed by Automerge.
#[derive(uniffi::Object)]
pub struct Document {
    inner: Mutex<DocInner>,
}

#[uniffi::export]
impl Document {
    /// Create a new empty document.
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self {
            inner: Mutex::new(DocInner::new(AutoCommit::new())),
        }
    }

    /// Load a document from its binary representation.
    #[uniffi::constructor]
    pub fn load(data: Vec<u8>) -> Result<Self, AutomergeError> {
        let doc = AutoCommit::load(&data)
            .map_err(|e| AutomergeError::LoadError { msg: e.to_string() })?;
        Ok(Self {
            inner: Mutex::new(DocInner::new(doc)),
        })
    }

    /// Save the document to its binary representation.
    pub fn save(&self) -> Vec<u8> {
        let mut inner = self.inner.lock().unwrap();
        inner.doc.save()
    }

    /// Fork the document (create an independent copy).
    pub fn fork(&self) -> Self {
        let mut inner = self.inner.lock().unwrap();
        Self {
            inner: Mutex::new(DocInner::new(inner.doc.fork())),
        }
    }

    /// Merge another document into this one.
    pub fn merge(&self, other: &Document) -> Result<(), AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        let mut other_inner = other.inner.lock().unwrap();
        inner.doc.merge(&mut other_inner.doc)
            .map_err(|e| AutomergeError::MergeError { msg: e.to_string() })?;
        Ok(())
    }

    /// Get the actor ID as a hex string.
    pub fn get_actor_id(&self) -> String {
        let inner = self.inner.lock().unwrap();
        inner.doc.get_actor().to_hex_string()
    }

    // --- Root map operations ---

    /// Put a string value at a key in the root map.
    pub fn put_in_map(&self, key: String, value: String) -> Result<(), AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        inner.doc.put(ROOT, &key, value)
            .map_err(|e| AutomergeError::PutError { msg: e.to_string() })
    }

    /// Put an integer value at a key in the root map.
    pub fn put_int_in_map(&self, key: String, value: i64) -> Result<(), AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        inner.doc.put(ROOT, &key, value)
            .map_err(|e| AutomergeError::PutError { msg: e.to_string() })
    }

    /// Put a double value at a key in the root map.
    pub fn put_double_in_map(&self, key: String, value: f64) -> Result<(), AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        inner.doc.put(ROOT, &key, value)
            .map_err(|e| AutomergeError::PutError { msg: e.to_string() })
    }

    /// Put a boolean value at a key in the root map.
    pub fn put_bool_in_map(&self, key: String, value: bool) -> Result<(), AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        inner.doc.put(ROOT, &key, value)
            .map_err(|e| AutomergeError::PutError { msg: e.to_string() })
    }

    /// Put a null value at a key in the root map.
    pub fn put_null_in_map(&self, key: String) -> Result<(), AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        inner.doc.put(ROOT, &key, ())
            .map_err(|e| AutomergeError::PutError { msg: e.to_string() })
    }

    /// Create a new nested object at a key in the root map. Returns a handle string.
    pub fn put_object_in_map(&self, key: String, obj_type: AmObjType) -> Result<String, AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        let obj_id = inner.doc.put_object(ROOT, &key, obj_type.into())
            .map_err(|e| AutomergeError::PutError { msg: e.to_string() })?;
        let handle = inner.register_obj(obj_id);
        Ok(handle)
    }

    /// Get a string value from the root map.
    pub fn get_string_from_map(&self, key: String) -> Result<Option<String>, AutomergeError> {
        let inner = self.inner.lock().unwrap();
        match inner.doc.get(ROOT, &key) {
            Ok(Some((value, _))) => {
                match value.into_string() {
                    Ok(s) => Ok(Some(s)),
                    Err(_) => Ok(None),
                }
            }
            Ok(None) => Ok(None),
            Err(e) => Err(AutomergeError::GetError { msg: e.to_string() }),
        }
    }

    /// Get an integer value from the root map.
    pub fn get_int_from_map(&self, key: String) -> Result<Option<i64>, AutomergeError> {
        let inner = self.inner.lock().unwrap();
        match inner.doc.get(ROOT, &key) {
            Ok(Some((value, _))) => Ok(value.to_i64()),
            Ok(None) => Ok(None),
            Err(e) => Err(AutomergeError::GetError { msg: e.to_string() }),
        }
    }

    /// Get all keys from the root map.
    pub fn keys(&self) -> Vec<String> {
        let inner = self.inner.lock().unwrap();
        inner.doc.keys(ROOT).collect()
    }

    // --- Text operations ---

    /// Create a text object at a key in the root map. Returns a handle string.
    pub fn put_text_in_map(&self, key: String) -> Result<String, AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        let obj_id = inner.doc.put_object(ROOT, &key, ObjType::Text)
            .map_err(|e| AutomergeError::PutError { msg: e.to_string() })?;
        let handle = inner.register_obj(obj_id);
        Ok(handle)
    }

    /// Get the text content from a text object by its handle.
    pub fn get_text(&self, handle: String) -> Result<String, AutomergeError> {
        let inner = self.inner.lock().unwrap();
        let obj_id = inner.resolve_obj(&handle)?;
        inner.doc.text(obj_id)
            .map_err(|e| AutomergeError::GetError { msg: e.to_string() })
    }

    /// Splice text into a text object.
    pub fn splice_text(
        &self,
        handle: String,
        index: u64,
        delete: i64,
        text: String,
    ) -> Result<(), AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        let obj_id = inner.resolve_obj(&handle)?.clone();
        inner.doc.splice_text(&obj_id, index as usize, delete as isize, &text)
            .map_err(|e| AutomergeError::PutError { msg: e.to_string() })
    }

    // --- Nested map operations ---

    /// Put a string value at a key in a nested map object.
    pub fn put_in_nested_map(&self, handle: String, key: String, value: String) -> Result<(), AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        let obj_id = inner.resolve_obj(&handle)?.clone();
        inner.doc.put(&obj_id, &key, value)
            .map_err(|e| AutomergeError::PutError { msg: e.to_string() })
    }

    /// Put an integer value at a key in a nested map object.
    pub fn put_int_in_nested_map(&self, handle: String, key: String, value: i64) -> Result<(), AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        let obj_id = inner.resolve_obj(&handle)?.clone();
        inner.doc.put(&obj_id, &key, value)
            .map_err(|e| AutomergeError::PutError { msg: e.to_string() })
    }

    /// Get a string value from a nested map object.
    pub fn get_string_from_nested_map(&self, handle: String, key: String) -> Result<Option<String>, AutomergeError> {
        let inner = self.inner.lock().unwrap();
        let obj_id = inner.resolve_obj(&handle)?;
        match inner.doc.get(obj_id, &key) {
            Ok(Some((value, _))) => {
                match value.into_string() {
                    Ok(s) => Ok(Some(s)),
                    Err(_) => Ok(None),
                }
            }
            Ok(None) => Ok(None),
            Err(e) => Err(AutomergeError::GetError { msg: e.to_string() }),
        }
    }

    /// Create a nested object inside another object. Returns a handle string.
    pub fn put_object_in_nested_map(&self, handle: String, key: String, obj_type: AmObjType) -> Result<String, AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        let parent_id = inner.resolve_obj(&handle)?.clone();
        let obj_id = inner.doc.put_object(&parent_id, &key, obj_type.into())
            .map_err(|e| AutomergeError::PutError { msg: e.to_string() })?;
        let new_handle = inner.register_obj(obj_id);
        Ok(new_handle)
    }

    /// Get all keys from a nested map object.
    pub fn keys_in_nested_map(&self, handle: String) -> Result<Vec<String>, AutomergeError> {
        let inner = self.inner.lock().unwrap();
        let obj_id = inner.resolve_obj(&handle)?;
        Ok(inner.doc.keys(obj_id).collect())
    }

    // --- List operations ---

    /// Create a list at a key in the root map. Returns a handle string.
    pub fn put_list_in_map(&self, key: String) -> Result<String, AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        let obj_id = inner.doc.put_object(ROOT, &key, ObjType::List)
            .map_err(|e| AutomergeError::PutError { msg: e.to_string() })?;
        let handle = inner.register_obj(obj_id);
        Ok(handle)
    }

    /// Insert a string value into a list at the given index.
    pub fn insert_in_list(&self, handle: String, index: u64, value: String) -> Result<(), AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        let obj_id = inner.resolve_obj(&handle)?.clone();
        inner.doc.insert(&obj_id, index as usize, value)
            .map_err(|e| AutomergeError::PutError { msg: e.to_string() })
    }

    /// Insert an integer value into a list at the given index.
    pub fn insert_int_in_list(&self, handle: String, index: u64, value: i64) -> Result<(), AutomergeError> {
        let mut inner = self.inner.lock().unwrap();
        let obj_id = inner.resolve_obj(&handle)?.clone();
        inner.doc.insert(&obj_id, index as usize, value)
            .map_err(|e| AutomergeError::PutError { msg: e.to_string() })
    }

    /// Get the length of a list.
    pub fn list_length(&self, handle: String) -> Result<u64, AutomergeError> {
        let inner = self.inner.lock().unwrap();
        let obj_id = inner.resolve_obj(&handle)?;
        Ok(inner.doc.length(obj_id) as u64)
    }

    /// Get a string value from a list at the given index.
    pub fn get_string_from_list(&self, handle: String, index: u64) -> Result<Option<String>, AutomergeError> {
        let inner = self.inner.lock().unwrap();
        let obj_id = inner.resolve_obj(&handle)?;
        match inner.doc.get(obj_id, index as usize) {
            Ok(Some((value, _))) => {
                match value.into_string() {
                    Ok(s) => Ok(Some(s)),
                    Err(_) => Ok(None),
                }
            }
            Ok(None) => Ok(None),
            Err(e) => Err(AutomergeError::GetError { msg: e.to_string() }),
        }
    }
}

/// Object types that can be created in an Automerge document.
#[derive(uniffi::Enum)]
pub enum AmObjType {
    Map,
    List,
    Text,
}

impl From<AmObjType> for ObjType {
    fn from(t: AmObjType) -> Self {
        match t {
            AmObjType::Map => ObjType::Map,
            AmObjType::List => ObjType::List,
            AmObjType::Text => ObjType::Text,
        }
    }
}

/// Errors that can occur when working with Automerge documents.
#[derive(uniffi::Error, Debug, thiserror::Error)]
pub enum AutomergeError {
    #[error("Failed to load document: {msg}")]
    LoadError { msg: String },
    #[error("Failed to merge documents: {msg}")]
    MergeError { msg: String },
    #[error("Failed to put value: {msg}")]
    PutError { msg: String },
    #[error("Failed to get value: {msg}")]
    GetError { msg: String },
    #[error("Invalid object ID: {msg}")]
    InvalidObjectId { msg: String },
}
