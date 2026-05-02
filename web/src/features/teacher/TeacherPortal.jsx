import { useEffect, useMemo, useState } from 'react';
import { api } from '../../services/api';
import { AttendanceModal } from './AttendanceModal';
import { CourseDetail } from './CourseDetail';
import { CoursesWorkspace } from './CoursesWorkspace';
import { RecordsDrawer } from './RecordsDrawer';
import { TeacherLeaveRequests } from './TeacherLeaveRequests';
import { TeacherProfile } from './TeacherProfile';
import { TodayBoard } from './TodayBoard';

export function TeacherPortal({ session, view, setView, setBreadcrumb, logout }) {
  const client = useMemo(() => api.withToken(session.token), [session.token]);
  const [courses, setCourses] = useState([]);
  const [coursesError, setCoursesError] = useState('');
  const [selectedCourse, setSelectedCourse] = useState(null);
  const [activeTab, setActiveTab] = useState('records');
  const [detail, setDetail] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [students, setStudents] = useState([]);
  const [liveSession, setLiveSession] = useState(null);
  const [drawerSession, setDrawerSession] = useState(null);
  const [drawerRecords, setDrawerRecords] = useState([]);
  const [loadingDetail, setLoadingDetail] = useState(false);

  useEffect(() => {
    if (view === 'today') setBreadcrumb(['首页', '今日课表']);
    else if (view === 'leave-requests') setBreadcrumb(['首页', '申报审核']);
    else if (view === 'profile') setBreadcrumb(['首页', '个人中心']);
    else setBreadcrumb(['首页', selectedCourse ? '课程详情' : '我的课程']);
  }, [setBreadcrumb, selectedCourse, view]);

  useEffect(() => {
    let cancelled = false;
    client.get('/teacher/courses')
      .then((data) => {
        if (!cancelled) setCourses(data);
      })
      .catch((err) => {
        if (!cancelled) setCoursesError(err.message ?? '课程加载失败');
      });
    return () => {
      cancelled = true;
    };
  }, [client]);

  useEffect(() => {
    if (view !== 'courses' || !selectedCourse) return undefined;
    let cancelled = false;
    setLoadingDetail(true);
    Promise.all([
      client.get(`/teacher/courses/${selectedCourse.id}`),
      client.get(`/teacher/courses/${selectedCourse.id}/attendance-sessions`),
    ])
      .then(([nextDetail, nextSessions]) => {
        if (!cancelled) {
          setDetail(nextDetail);
          setSessions(nextSessions);
        }
      })
      .catch((err) => {
        if (!cancelled) setCoursesError(err.message ?? '课程详情加载失败');
      })
      .finally(() => {
        if (!cancelled) setLoadingDetail(false);
      });
    return () => {
      cancelled = true;
    };
  }, [client, selectedCourse, view]);

  useEffect(() => {
    if (view !== 'courses' || activeTab !== 'students' || !selectedCourse) return undefined;
    let cancelled = false;
    client.get(`/teacher/courses/${selectedCourse.id}/students`).then((data) => {
      if (!cancelled) setStudents(data);
    });
    return () => {
      cancelled = true;
    };
  }, [activeTab, client, selectedCourse, view]);

  function openDetail(course) {
    setView('courses');
    setSelectedCourse(course);
    setActiveTab('records');
  }

  async function refreshCourseData() {
    if (!selectedCourse) return;
    setSessions(await client.get(`/teacher/courses/${selectedCourse.id}/attendance-sessions`));
    if (activeTab === 'students') {
      setStudents(await client.get(`/teacher/courses/${selectedCourse.id}/students`));
    }
  }

  async function deleteSession(sessionId) {
    await client.delete(`/teacher/attendance-sessions/${sessionId}`);
    await refreshCourseData();
  }

  async function openRecordsDrawer(sessionRow) {
    setDrawerSession(sessionRow);
    setDrawerRecords(await client.get(`/teacher/attendance-sessions/${sessionRow.id}/records`));
  }

  if (view === 'leave-requests') {
    return <TeacherLeaveRequests client={client} onAuthExpired={logout} />;
  }

  if (view === 'profile') {
    return <TeacherProfile client={client} logout={logout} />;
  }

  if (view === 'today') {
    return (
      <>
        <TodayBoard client={client} courses={courses} onLive={setLiveSession} />
        {liveSession && (
          <AttendanceModal
            client={client}
            session={liveSession}
            headline={liveSession.headline}
            subline={liveSession.subline}
            onClose={() => setLiveSession(null)}
          />
        )}
      </>
    );
  }

  return (
    <>
      {!selectedCourse ? (
        <CoursesWorkspace
          courses={courses}
          error={coursesError}
          onOpenDetail={openDetail}
        />
      ) : (
        <CourseDetail
          client={client}
          course={detail ?? selectedCourse}
          sessions={sessions}
          students={students}
          activeTab={activeTab}
          loading={loadingDetail}
          onBack={() => setSelectedCourse(null)}
          onTab={setActiveTab}
          onOpenRecords={openRecordsDrawer}
          onStudentsChange={setStudents}
          onDeleteSession={deleteSession}
        />
      )}
      {drawerSession && (
        <RecordsDrawer
          session={drawerSession}
          records={drawerRecords}
          onClose={() => {
            setDrawerSession(null);
            setDrawerRecords([]);
          }}
        />
      )}
    </>
  );
}
