import unittest
from threading import Lock, Event
from identity_verification.jobs import VerificationJobs


class JobTests(unittest.TestCase):
    def test_real_progress_busy_cleanup_and_failure(self):
        entered, release, cleaned = Event(), Event(), Event()
        store = VerificationJobs(Lock())
        def work(progress):
            progress({'stage':'ANALYZING_VIDEO', 'sampledFrames':3})
            entered.set()
            if not release.wait(3):
                raise RuntimeError('Test timeout')
            return {'automaticApproval':False}
        try:
            key = store.submit(work, cleaned.set)
            self.assertTrue(entered.wait(2))
            current = store.get(key)
            self.assertEqual('RUNNING', current['state'])
            self.assertEqual('ANALYZING_VIDEO', current['progress']['stage'])
            self.assertEqual('ANALYZING_VIDEO', current['events'][-1]['stage'])
            self.assertGreaterEqual(current['events'][-1]['elapsedMs'], 0)
            self.assertIsNone(current['progress']['percentage'])
            with self.assertRaises(RuntimeError):
                store.submit(work, lambda:None)
            release.set()
            self.assertTrue(cleaned.wait(2))
        finally:
            release.set()
            store.close()
        self.assertEqual('COMPLETED', store.get(key)['state'])
        self.assertFalse(store.get(key)['result']['automaticApproval'])
        self.assertNotIn('_started', store.get(key))
        store = VerificationJobs(Lock())
        try:
            key = store.submit(lambda p: (_ for _ in ()).throw(ValueError('private input')), lambda:None)
        finally:
            store.close()
        failure = store.get(key)
        self.assertEqual('FAILED', failure['state'])
        self.assertEqual(422, failure['error']['status'])
        self.assertNotIn('private input', str(failure))

class FaceAssessmentTests(unittest.TestCase):
    def test_unconfigured_threshold_is_not_a_match_even_with_high_scores(self):
        from identity_verification.face_matching import assess_match
        self.assertEqual('NOT_CALIBRATED',assess_match([.99]*5)['status'])
        self.assertEqual('NO_USABLE_FACE',assess_match([])['status'])
        self.assertEqual('INSUFFICIENT_EVIDENCE',assess_match([.99])['status'])

    def test_experimental_ranges_and_evaluation_require_both_labels(self):
        from identity_verification.face_matching import assess_match,evaluate_scores
        self.assertEqual('LOW_SIMILARITY',assess_match([.1]*5,.2,.7)['status'])
        self.assertEqual('INCONCLUSIVE',assess_match([.5]*5,.2,.7)['status'])
        self.assertEqual('LIKELY_MATCH',assess_match([.9]*5,.2,.7)['status'])
        with self.assertRaises(ValueError):evaluate_scores([dict(captureId='a',samePerson=True,score=.9)],.2,.7)
        result=evaluate_scores([dict(captureId='a',samePerson=True,score=.9),dict(captureId='b',samePerson=False,score=.1)],.2,.7)
        self.assertEqual(0,result['falseMatchRateAtHigh'])
        self.assertFalse(result['automaticApproval'])
        with self.assertRaises(ValueError):assess_match([float('nan')])

    def test_policy_configuration_requires_version_and_pinned_model(self):
        from identity_verification.config import Settings
        with self.assertRaises(ValueError):Settings(api_key='test-key-only-not-secret-1234567890',face_match_low_threshold=.2,face_match_high_threshold=.7,_env_file=None)

if __name__ == '__main__':
    unittest.main()
