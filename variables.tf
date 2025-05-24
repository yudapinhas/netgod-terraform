variable "project_id" {
  type        = string
  description = "GCP project ID"
}

variable "region" {
  type        = string
  default     = "us-east4"
  description = "GCP region"
}

variable "bucket_name" {
  type        = string
  description = "Name of the GCS bucket"
}

variable "location" {
  type        = string
  default     = "US"
  description = "Bucket location"
}

variable "force_destroy" {
  type        = bool
  default     = false
  description = "Allow destroy even if bucket contains objects"
}