"""Regression coverage for observed kubectl --from=cronjob owner semantics."""
import copy
import unittest

from verify_kind_schedule import scheduled_job_metadata


class ScheduledJobEventTest(unittest.TestCase):
    def setUp(self):
        self.event = {
            'type': 'ADDED',
            'object': {'metadata': {
                'name': 'scheduled-job',
                'uid': 'job-uid',
                'annotations': {'batch.kubernetes.io/cronjob-scheduled-timestamp': '2026-10-24T01:15:00Z'},
                'ownerReferences': [{'uid': 'cron-uid', 'controller': True}],
            }},
        }

    def test_actual_scheduled_job_is_selected(self):
        self.assertEqual(scheduled_job_metadata(self.event, 'cron-uid', set())['name'], 'scheduled-job')

    def test_manual_job_with_same_owner_is_not_schedule_evidence(self):
        event = copy.deepcopy(self.event)
        event['object']['metadata'].pop('annotations')
        self.assertIsNone(scheduled_job_metadata(event, 'cron-uid', set()))

    def test_other_owner_is_ignored(self):
        self.assertIsNone(scheduled_job_metadata(self.event, 'other-uid', set()))

    def test_modified_event_is_ignored(self):
        self.event['type'] = 'MODIFIED'
        self.assertIsNone(scheduled_job_metadata(self.event, 'cron-uid', set()))

    def test_previous_scheduled_job_is_not_evidence_for_current_fixture(self):
        self.assertIsNone(scheduled_job_metadata(self.event, 'cron-uid', {'job-uid'}))


if __name__ == '__main__':
    unittest.main()
